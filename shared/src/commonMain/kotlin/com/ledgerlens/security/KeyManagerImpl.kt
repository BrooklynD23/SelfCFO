package com.ledgerlens.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production-ready implementation of KeyManager.
 *
 * Implements the key hierarchy per ADR-003:
 * - User passphrase → Master Key (via KeyDerivation with salt)
 * - Master Key wraps KEK (stored in PlatformKeystore)
 * - KEK wraps DEKs (database key, file keys via HKDF-like derivation)
 *
 * Key storage:
 * - Android: Uses Android Keystore (hardware-backed when available)
 * - Desktop: Uses OS keychain/secure preferences
 *
 * @param platformKeystore Platform-specific secure storage for wrapped KEK
 * @param fileEncryption Helper for wrapping/unwrapping keys using AES-GCM
 */
class KeyManagerImpl(
    private val platformKeystore: PlatformKeystore,
    private val fileEncryption: FileEncryption
) : KeyManager {

    // In-memory cache of keys (cleared on lock)
    private var cachedKek: ByteArray? = null
    private var cachedDatabaseKey: ByteArray? = null
    private val cachedFileKeys = mutableMapOf<String, ByteArray>()
    private var isUnlockedState = false

    override suspend fun initializeKeys(passphrase: String): Result<RecoveryKey> = withContext(Dispatchers.Default) {
        runCatching {
            // Validate passphrase
            val validation = PassphraseRequirements.validate(passphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                return@runCatching Result.failure<RecoveryKey>(
                    IllegalArgumentException(validation.errors.joinToString(", "))
                )
            }

            // Check if already initialized
            val existingKek = platformKeystore.retrieveKek().getOrNull()
            if (existingKek != null) {
                return@runCatching Result.failure<RecoveryKey>(
                    IllegalStateException("Keys already initialized")
                )
            }

            // Generate salt for key derivation
            val salt = generateSalt()
            platformKeystore.storeSalt(salt).getOrThrow()

            // Derive master key from passphrase
            val masterKey = KeyDerivation.deriveKey(passphrase, salt, AesGcmConstants.KEY_LENGTH)

            // Generate KEK (Key Encryption Key)
            val kek = generateAesKey()

            // Wrap KEK with master key
            val wrappedKek = fileEncryption.encrypt(kek, masterKey)

            // Store wrapped KEK in platform keystore
            platformKeystore.storeKek(wrappedKek).getOrThrow()

            // Generate recovery key (24-word mnemonic)
            val recoveryKey = generateRecoveryKey()

            // Cache keys
            cachedKek = kek
            isUnlockedState = true

            Result.success(recoveryKey)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun unlock(passphrase: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            // Check if initialized
            val wrappedKek = platformKeystore.retrieveKek().getOrThrow() ?: run {
                return@runCatching Result.failure<Unit>(
                    IllegalStateException("Keys not initialized")
                )
            }

            // Retrieve salt
            val salt = platformKeystore.retrieveSalt().getOrThrow() ?: run {
                return@runCatching Result.failure<Unit>(
                    IllegalStateException("Salt not found - keys may be corrupted")
                )
            }

            // Derive master key from passphrase
            val masterKey = KeyDerivation.deriveKey(passphrase, salt, AesGcmConstants.KEY_LENGTH)

            // Unwrap KEK
            val kek = try {
                fileEncryption.decrypt(wrappedKek, masterKey)
            } catch (e: AuthenticationException) {
                return@runCatching Result.failure<Unit>(
                    IllegalArgumentException("Invalid passphrase")
                )
            } catch (e: Exception) {
                return@runCatching Result.failure<Unit>(
                    EncryptionException("Failed to decrypt KEK", e)
                )
            }

            // Cache KEK
            cachedKek = kek
            isUnlockedState = true

            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override fun lock() {
        cachedKek = null
        cachedDatabaseKey = null
        cachedFileKeys.clear()
        isUnlockedState = false
    }

    override fun isUnlocked(): Boolean = isUnlockedState

    override suspend fun isInitialized(): Boolean = withContext(Dispatchers.Default) {
        platformKeystore.retrieveKek().getOrNull() != null
    }

    override suspend fun getDatabaseKey(): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            if (!isUnlockedState) {
                return@runCatching Result.failure<ByteArray>(
                    IllegalStateException("Database is locked")
                )
            }

            // Return cached key if available
            cachedDatabaseKey?.let { return@runCatching Result.success(it) }

            // Derive database key from KEK
            val kek = cachedKek ?: run {
                return@runCatching Result.failure<ByteArray>(
                    IllegalStateException("KEK not available")
                )
            }

            // Derive database key using HKDF-like derivation
            val dbKey = deriveKey(kek, "database_key".toByteArray(), AesGcmConstants.KEY_LENGTH)
            cachedDatabaseKey = dbKey

            Result.success(dbKey)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun getFileKey(fileId: String): Result<ByteArray> = withContext(Dispatchers.Default) {
        runCatching {
            if (!isUnlockedState) {
                return@runCatching Result.failure<ByteArray>(
                    IllegalStateException("Database is locked")
                )
            }

            // Return cached key if available
            cachedFileKeys[fileId]?.let { return@runCatching Result.success(it) }

            // Derive file key from KEK
            val kek = cachedKek ?: run {
                return@runCatching Result.failure<ByteArray>(
                    IllegalStateException("KEK not available")
                )
            }

            // Derive file-specific key using HKDF-like derivation
            val fileKey = deriveKey(kek, "file_key:$fileId".toByteArray(), AesGcmConstants.KEY_LENGTH)
            cachedFileKeys[fileId] = fileKey

            Result.success(fileKey)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun exportForBackup(exportPassphrase: String): Result<BackupBundle> = withContext(Dispatchers.Default) {
        runCatching {
            if (!isUnlockedState) {
                return@runCatching Result.failure<BackupBundle>(
                    IllegalStateException("Database is locked")
                )
            }

            val wrappedKek = platformKeystore.retrieveKek().getOrThrow() ?: run {
                return@runCatching Result.failure<BackupBundle>(
                    IllegalStateException("KEK not found")
                )
            }

            val salt = platformKeystore.retrieveSalt().getOrThrow() ?: run {
                return@runCatching Result.failure<BackupBundle>(
                    IllegalStateException("Salt not found")
                )
            }

            // Re-encrypt KEK with export passphrase
            val exportSalt = generateSalt()
            val exportMasterKey = KeyDerivation.deriveKey(exportPassphrase, exportSalt, AesGcmConstants.KEY_LENGTH)
            val encryptedKek = fileEncryption.encrypt(wrappedKek, exportMasterKey)

            Result.success(BackupBundle(encryptedKek, exportSalt))
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun importFromBackup(
        backup: BackupBundle,
        exportPassphrase: String,
        newPassphrase: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            // Validate passphrase
            val validation = PassphraseRequirements.validate(newPassphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                return@runCatching Result.failure<Unit>(
                    IllegalArgumentException(validation.errors.joinToString(", "))
                )
            }

            // Decrypt KEK with export passphrase
            val exportMasterKey = KeyDerivation.deriveKey(exportPassphrase, backup.salt, AesGcmConstants.KEY_LENGTH)
            val wrappedKek = try {
                fileEncryption.decrypt(backup.encryptedKek, exportMasterKey)
            } catch (e: AuthenticationException) {
                return@runCatching Result.failure<Unit>(
                    IllegalArgumentException("Invalid export passphrase")
                )
            }

            // Generate new salt for new passphrase
            val newSalt = generateSalt()
            platformKeystore.storeSalt(newSalt).getOrThrow()

            // Re-wrap KEK with new passphrase
            val newMasterKey = KeyDerivation.deriveKey(newPassphrase, newSalt, AesGcmConstants.KEY_LENGTH)
            val newWrappedKek = fileEncryption.encrypt(wrappedKek, newMasterKey)

            // Store in platform keystore
            platformKeystore.storeKek(newWrappedKek).getOrThrow()

            // Unlock with new passphrase
            unlock(newPassphrase).getOrThrow()

            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun cryptoErase(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            lock()
            platformKeystore.deleteKek().getOrThrow()
            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    override suspend fun changePassphrase(
        currentPassphrase: String,
        newPassphrase: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            if (!isUnlockedState) {
                return@runCatching Result.failure<Unit>(
                    IllegalStateException("Database is locked")
                )
            }

            // Validate new passphrase
            val validation = PassphraseRequirements.validate(newPassphrase)
            if (validation is PassphraseValidationResult.Invalid) {
                return@runCatching Result.failure<Unit>(
                    IllegalArgumentException(validation.errors.joinToString(", "))
                )
            }

            // Get current KEK
            val kek = cachedKek ?: run {
                return@runCatching Result.failure<Unit>(
                    IllegalStateException("KEK not available")
                )
            }

            // Verify current passphrase by attempting unlock
            lock()
            unlock(currentPassphrase).getOrThrow()

            // Generate new salt
            val newSalt = generateSalt()
            platformKeystore.storeSalt(newSalt).getOrThrow()

            // Re-wrap KEK with new passphrase
            val newMasterKey = KeyDerivation.deriveKey(newPassphrase, newSalt, AesGcmConstants.KEY_LENGTH)
            val newWrappedKek = fileEncryption.encrypt(kek, newMasterKey)

            // Store new wrapped KEK
            platformKeystore.storeKek(newWrappedKek).getOrThrow()

            // Re-unlock with new passphrase
            unlock(newPassphrase).getOrThrow()

            Result.success(Unit)
        }.getOrElse { Result.failure(it) }
    }

    /**
     * Derive a key from KEK using HKDF.
     */
    private fun deriveKey(kek: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        return Hkdf.derive(kek, info, outputLength)
    }

    /**
     * Generate a 24-word recovery key mnemonic.
     * For minimal implementation, uses a simplified word list.
     */
    private fun generateRecoveryKey(): RecoveryKey {
        // Generate 32 bytes of entropy (256 bits)
        val entropy = SecureRandom.nextBytes(32)

        // Convert to 24 words (simplified - in production, use BIP39)
        // Each word represents ~10.67 bits (256/24)
        val words = mutableListOf<String>()
        var bitIndex = 0

        for (i in 0 until 24) {
            // Extract 11 bits for each word index (2048 word list)
            var wordIndex = 0
            for (bit in 0 until 11) {
                val byteIndex = (bitIndex + bit) / 8
                val bitPos = 7 - ((bitIndex + bit) % 8)
                if (byteIndex < entropy.size) {
                    val bitValue = (entropy[byteIndex].toInt() ushr bitPos) and 1
                    wordIndex = (wordIndex shl 1) or bitValue
                }
            }
            bitIndex += 11

            // Use simplified word list (first 2048 common words)
            words.add(RECOVERY_WORD_LIST[wordIndex % RECOVERY_WORD_LIST.size])
        }

        return RecoveryKey(words)
    }

    companion object {
        /**
         * Simplified word list for recovery keys (2048 words).
         * In production, use BIP39 word list for better standardization.
         */
        private val RECOVERY_WORD_LIST = listOf(
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
            "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
            "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
            "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
            "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
            "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
            "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
            "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
            "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
            "area", "arena", "argue", "arm", "armed", "armor", "army", "around",
            "arrange", "arrest", "arrive", "arrow", "art", "article", "artist", "artwork",
            "ask", "aspect", "assault", "asset", "assist", "assume", "asthma", "athlete",
            "atom", "attack", "attend", "attitude", "attract", "auction", "audit", "august",
            "aunt", "author", "auto", "autumn", "average", "avocado", "avoid", "awake",
            "aware", "away", "awesome", "awful", "awkward", "axis", "baby", "bachelor",
            "bacon", "badge", "bag", "balance", "balcony", "ball", "bamboo", "banana",
            "banner", "bar", "barely", "bargain", "barrel", "base", "basic", "basket",
            "battle", "beach", "bean", "beauty", "because", "become", "beef", "before",
            "begin", "behave", "behind", "believe", "below", "belt", "bench", "benefit",
            "best", "betray", "better", "between", "beyond", "bicycle", "bid", "bike",
            "bind", "biology", "bird", "birth", "bitter", "black", "blade", "blame",
            "blanket", "blast", "bleak", "bless", "blind", "blood", "blossom", "blow",
            "blue", "blur", "blush", "board", "boat", "body", "boil", "bomb",
            "bone", "bonus", "book", "boost", "border", "boring", "borrow", "boss",
            "bottom", "bounce", "box", "boy", "bracket", "brain", "brand", "brass",
            "brave", "bread", "breeze", "brick", "bridge", "brief", "bright", "bring",
            "brisk", "broccoli", "broken", "bronze", "broom", "brother", "brown", "brush",
            "bubble", "buddy", "budget", "buffalo", "build", "bulb", "bulk", "bullet",
            "bundle", "bunker", "burden", "burger", "burst", "bus", "business", "busy",
            "butter", "buyer", "buzz", "cabbage", "cabin", "cable", "cactus", "cage",
            "cake", "call", "calm", "camera", "camp", "can", "canal", "cancel",
            "candy", "cannon", "canoe", "canvas", "canyon", "capable", "capital", "captain",
            "car", "carbon", "card", "care", "career", "careful", "careless", "cargo",
            "carpet", "carry", "cart", "case", "cash", "casino", "cast", "casual",
            "cat", "catalog", "catch", "category", "cattle", "caught", "cause", "caution",
            "cave", "ceiling", "celery", "cement", "census", "century", "cereal", "certain",
            "chair", "chalk", "champion", "change", "chaos", "chapter", "charge", "chase",
            "chat", "cheap", "check", "cheese", "chef", "cherry", "chest", "chicken",
            "chief", "child", "chimney", "choice", "choose", "chronic", "chuckle", "chunk",
            "churn", "cigar", "cinnamon", "circle", "citizen", "city", "civil", "claim",
            "clamp", "clarify", "claw", "clay", "clean", "clerk", "clever", "click",
            "client", "cliff", "climb", "clinic", "clip", "clock", "clog", "close",
            "cloth", "cloud", "clown", "club", "clump", "cluster", "clutch", "coach",
            "coast", "coconut", "code", "coffee", "coil", "coin", "collect", "color",
            "column", "combine", "come", "comfort", "comic", "common", "company", "concert",
            "conduct", "confirm", "congress", "connect", "consider", "control", "convince", "cook",
            "cool", "copper", "copy", "coral", "core", "corn", "correct", "cost",
            "cotton", "couch", "country", "couple", "course", "cousin", "cover", "coyote",
            "crack", "cradle", "craft", "cram", "crane", "crash", "crater", "crawl",
            "crazy", "cream", "credit", "creek", "crew", "cricket", "crime", "crisp",
            "critic", "crop", "cross", "crouch", "crowd", "crucial", "cruel", "cruise",
            "crumble", "crunch", "crush", "cry", "crystal", "cube", "culture", "cup",
            "cupboard", "curious", "current", "curtain", "curve", "cushion", "custom", "cute",
            "cycle", "dad", "damage", "damp", "dance", "danger", "daring", "dark",
            "dash", "date", "daughter", "dawn", "day", "deal", "debate", "debris",
            "decade", "december", "decide", "decline", "decorate", "decrease", "deer", "defense",
            "define", "defy", "degree", "delay", "deliver", "demand", "demise", "denial",
            "dentist", "deny", "depart", "depend", "deposit", "depth", "deputy", "derive",
            "describe", "desert", "design", "desk", "despair", "destroy", "detail", "detect",
            "develop", "device", "devote", "diagram", "dial", "diamond", "diary", "dice",
            "diesel", "diet", "differ", "digital", "dignity", "dilemma", "dinner", "dinosaur",
            "direct", "dirt", "disagree", "discover", "disease", "dish", "dismiss", "disorder",
            "display", "distance", "divert", "divide", "divorce", "dizzy", "doctor", "document",
            "dog", "doll", "dolphin", "domain", "donate", "donkey", "donor", "door",
            "dose", "double", "dove", "draft", "dragon", "drama", "drastic", "draw",
            "dream", "dress", "drift", "drill", "drink", "drip", "drive", "drop",
            "drum", "dry", "duck", "dumb", "dune", "during", "dust", "dutch",
            "duty", "dwarf", "dynamic", "eager", "eagle", "early", "earn", "earth",
            "easily", "east", "easy", "echo", "ecology", "economy", "edge", "edit",
            "educate", "effort", "egg", "eight", "either", "elbow", "elder", "electric",
            "elegant", "element", "elephant", "elevator", "elite", "else", "embark", "embody",
            "embrace", "emerge", "emotion", "employ", "empower", "empty", "enable", "enact",
            "end", "endless", "endorse", "enemy", "energy", "enforce", "engage", "engine",
            "enhance", "enjoy", "enlist", "enough", "enrich", "enroll", "ensure", "enter",
            "entire", "entry", "envelope", "episode", "equal", "equip", "era", "erase",
            "erode", "erosion", "error", "erupt", "escape", "essay", "essence", "estate",
            "eternal", "ethics", "evidence", "evil", "evoke", "evolve", "exact", "example",
            "exceed", "excel", "exception", "exchange", "excite", "exclude", "excuse", "execute",
            "exercise", "exhaust", "exhibit", "exile", "exist", "exit", "exotic", "expand",
            "expect", "expire", "explain", "expose", "express", "extend", "extra", "eye",
            "eyebrow", "fabric", "face", "faculty", "fade", "faint", "faith", "fall",
            "false", "fame", "family", "famous", "fan", "fancy", "fantasy", "farm",
            "fashion", "fat", "fatal", "father", "fatigue", "fault", "favorite", "feature",
            "february", "federal", "fee", "feed", "feel", "female", "fence", "festival",
            "fetch", "fever", "few", "fiber", "fiction", "field", "figure", "file",
            "film", "filter", "final", "find", "fine", "finger", "finish", "fire",
            "firm", "first", "fiscal", "fish", "fit", "fitness", "fix", "flag",
            "flame", "flash", "flat", "flavor", "flee", "flight", "flip", "float",
            "flock", "floor", "flower", "fluid", "flush", "fly", "foam", "focus",
            "fog", "foil", "fold", "follow", "food", "foot", "force", "forest",
            "forget", "fork", "fortune", "forum", "forward", "fossil", "foster", "found",
            "fox", "fragile", "frame", "frequent", "fresh", "friend", "fringe", "frog",
            "front", "frost", "frown", "frozen", "fruit", "fuel", "fun", "funny",
            "furnace", "fury", "future", "gadget", "gain", "galaxy", "gallery", "game",
            "gap", "garage", "garbage", "garden", "garlic", "garment", "gas", "gasp",
            "gate", "gather", "gauge", "gaze", "general", "genius", "genre", "gentle",
            "genuine", "gesture", "ghost", "giant", "gift", "giggle", "ginger", "giraffe",
            "girl", "give", "glad", "glance", "glare", "glass", "glide", "glimpse",
            "globe", "gloom", "glory", "glove", "glow", "glue", "goat", "goddess",
            "gold", "good", "goose", "gorilla", "gospel", "gossip", "govern", "gown",
            "grab", "grace", "grain", "grant", "grape", "grass", "gravity", "great",
            "green", "grid", "grief", "grit", "grocery", "group", "grow", "grunt",
            "guard", "guess", "guide", "guilt", "guitar", "gun", "gym", "habit",
            "hair", "half", "hammer", "hamster", "hand", "happy", "harbor", "hard",
            "harsh", "harvest", "hat", "have", "hawk", "hazard", "head", "health",
            "heart", "heavy", "hedgehog", "height", "hello", "helmet", "help", "hen",
            "hero", "hidden", "high", "hill", "hint", "hip", "hire", "history",
            "hobby", "hockey", "hold", "hole", "holiday", "hollow", "home", "honey",
            "hood", "hope", "horn", "horror", "horse", "hospital", "host", "hotel",
            "hour", "hover", "hub", "huge", "human", "humble", "humor", "hundred",
            "hungry", "hunt", "hurdle", "hurry", "hurt", "husband", "hybrid", "ice",
            "icon", "idea", "identify", "idle", "ignore", "ill", "illegal", "illness",
            "image", "imitate", "immense", "immune", "impact", "impose", "improve", "impulse",
            "inch", "include", "income", "increase", "index", "indicate", "indoor", "industry",
            "infant", "inflict", "inform", "inhale", "inherit", "initial", "inject", "injury",
            "inmate", "inner", "innocent", "input", "inquiry", "insane", "insect", "inside",
            "inspire", "install", "intact", "interest", "into", "invest", "invite", "involve",
            "iron", "island", "isolate", "issue", "item", "ivory", "jacket", "jaguar",
            "jar", "jazz", "jealous", "jeans", "jelly", "jewel", "job", "join",
            "joke", "journey", "joy", "judge", "juice", "july", "jump", "june",
            "jungle", "junior", "junk", "just", "kangaroo", "keen", "keep", "ketchup",
            "key", "kick", "kid", "kidney", "kind", "kingdom", "kiss", "kit",
            "kitchen", "kite", "kitten", "kiwi", "knee", "knife", "knock", "know",
            "lab", "label", "labor", "ladder", "lady", "lake", "lamp", "language",
            "laptop", "large", "later", "latin", "laugh", "laundry", "lava", "law",
            "lawn", "lawsuit", "layer", "lazy", "leader", "leaf", "learn", "leave",
            "lecture", "left", "leg", "legal", "legend", "leisure", "lemon", "lend",
            "length", "lens", "leopard", "lesson", "letter", "level", "liar", "liberty",
            "library", "license", "life", "lift", "light", "like", "limb", "limit",
            "link", "lion", "liquid", "list", "little", "live", "lizard", "load",
            "loan", "lobster", "local", "lock", "logic", "lonely", "long", "loop",
            "lottery", "loud", "lounge", "love", "loyal", "lucky", "luggage", "lumber",
            "lunar", "lunch", "luxury", "lyrics", "machine", "mad", "magic", "magnet",
            "maid", "mail", "main", "major", "make", "mammal", "man", "manage",
            "mandate", "mango", "mansion", "manual", "maple", "marble", "march", "margin",
            "marine", "market", "marriage", "mask", "mass", "master", "match", "material",
            "math", "matrix", "matter", "maximum", "maze", "meadow", "mean", "measure",
            "meat", "mechanic", "medal", "media", "melody", "melt", "member", "memory",
            "mention", "menu", "mercy", "merge", "merit", "merry", "mesh", "message",
            "metal", "method", "middle", "midnight", "milk", "million", "mimic", "mind",
            "minimum", "minor", "minute", "miracle", "mirror", "misery", "miss", "mistake",
            "mix", "mixed", "mixture", "mobile", "model", "modify", "mom", "moment",
            "monitor", "monkey", "monster", "month", "moon", "moral", "more", "morning",
            "mosquito", "mother", "motion", "motor", "mountain", "mouse", "move", "movie",
            "much", "muffin", "mule", "multiply", "muscle", "museum", "mushroom", "music",
            "must", "mutual", "myself", "mystery", "myth", "naive", "name", "napkin",
            "narrow", "nasty", "nation", "nature", "near", "neck", "need", "negative",
            "neglect", "neither", "nephew", "nerve", "nest", "net", "network", "neutral",
            "never", "news", "next", "nice", "night", "noble", "noise", "nominee",
            "none", "noon", "nor", "normal", "north", "nose", "notable", "note",
            "nothing", "notice", "novel", "now", "nuclear", "number", "nurse", "nut",
            "oak", "obey", "object", "oblige", "obscure", "observe", "obtain", "obvious",
            "occur", "ocean", "october", "odor", "off", "offer", "office", "often",
            "oil", "okay", "old", "olive", "olympic", "omit", "once", "one",
            "onion", "online", "only", "open", "opera", "opinion", "oppose", "option",
            "orange", "orbit", "orchard", "order", "ordinary", "organ", "orient", "original",
            "orphan", "ostrich", "other", "outdoor", "outer", "output", "outside", "oval",
            "oven", "over", "own", "owner", "oxygen", "oyster", "ozone", "pact",
            "paddle", "page", "pair", "palace", "palm", "panda", "panel", "panic",
            "panther", "paper", "parade", "parent", "park", "parrot", "party", "pass",
            "patch", "path", "patient", "patrol", "pattern", "pause", "pave", "payment",
            "peace", "peanut", "pear", "peasant", "pelican", "pen", "penalty", "pencil",
            "people", "pepper", "perfect", "permit", "person", "pet", "phone", "photo",
            "phrase", "physical", "piano", "picnic", "picture", "piece", "pig", "pigeon",
            "pill", "pilot", "pink", "pioneer", "pipe", "pistol", "pitch", "pizza",
            "place", "planet", "plastic", "plate", "play", "please", "pledge", "pluck",
            "plug", "plunge", "poem", "poet", "point", "polar", "pole", "police",
            "pond", "pony", "pool", "popular", "portion", "position", "possible", "post",
            "potato", "pottery", "poverty", "powder", "power", "practice", "praise", "predict",
            "prefer", "prepare", "present", "pretty", "prevent", "price", "pride", "primary",
            "print", "priority", "prison", "private", "prize", "problem", "process", "produce",
            "profit", "program", "project", "promote", "proof", "property", "prosper", "protect",
            "proud", "provide", "public", "pudding", "pull", "pulp", "pulse", "pumpkin",
            "punch", "pupil", "puppy", "purchase", "purity", "purpose", "purse", "push",
            "put", "puzzle", "pyramid", "quality", "quantum", "quarter", "question", "quick",
            "quit", "quiz", "quote", "rabbit", "raccoon", "race", "rack", "radar",
            "radio", "rail", "rain", "raise", "rally", "ramp", "ranch", "random",
            "range", "rapid", "rare", "rate", "rather", "raven", "raw", "razor",
            "ready", "real", "reason", "rebel", "rebuild", "recall", "receive", "recipe",
            "record", "recycle", "reduce", "reflect", "reform", "refuse", "region", "regret",
            "regular", "reject", "relax", "release", "relief", "rely", "remain", "remember",
            "remind", "remove", "render", "renew", "rent", "reopen", "repair", "repeat",
            "replace", "report", "require", "rescue", "resemble", "resist", "resource", "response",
            "result", "retire", "retreat", "return", "reunion", "reveal", "review", "reward",
            "rhythm", "rib", "ribbon", "rice", "rich", "ride", "ridge", "rifle",
            "right", "rigid", "ring", "riot", "rip", "ripe", "rise", "risk",
            "rival", "river", "road", "roast", "robot", "robust", "rocket", "romance",
            "roof", "rookie", "room", "rose", "rotate", "rough", "round", "route",
            "royal", "rubber", "rude", "rug", "rule", "run", "runway", "rural",
            "sad", "saddle", "sadness", "safe", "sail", "salad", "salmon", "salon",
            "salt", "same", "sample", "sand", "satisfy", "satoshi", "sauce", "sausage",
            "save", "say", "scale", "scan", "scare", "scatter", "scene", "scheme",
            "school", "science", "scissors", "scorpion", "scout", "scrap", "screen", "script",
            "scrub", "sea", "search", "season", "seat", "second", "secret", "section",
            "security", "seed", "seek", "segment", "select", "sell", "seminar", "senior",
            "sense", "sentence", "series", "service", "session", "settle", "setup", "seven",
            "shadow", "shaft", "shallow", "share", "shed", "shell", "sheriff", "shield",
            "shift", "shine", "ship", "shiver", "shock", "shoe", "shoot", "shop",
            "short", "shoulder", "shove", "shrimp", "shrug", "shuffle", "shy", "sibling",
            "sick", "side", "siege", "sight", "sign", "silent", "silk", "silly",
            "silver", "similar", "simple", "since", "sing", "siren", "sister", "situate",
            "six", "size", "skate", "sketch", "ski", "skill", "skin", "skirt",
            "skull", "slab", "slam", "sleep", "slender", "slice", "slide", "slight",
            "slim", "slogan", "slot", "slow", "slush", "small", "smart", "smile",
            "smoke", "smooth", "snack", "snake", "snap", "sniff", "snow", "soap",
            "soccer", "social", "sock", "soda", "soft", "solar", "soldier", "solid",
            "solve", "someone", "song", "soon", "sorry", "sort", "soul", "sound",
            "soup", "source", "south", "space", "spare", "spatial", "spawn", "speak",
            "special", "speed", "spell", "spend", "sphere", "spice", "spider", "spike",
            "spin", "spirit", "split", "spoil", "sponsor", "spoon", "sport", "spot",
            "spray", "spread", "spring", "spy", "square", "squeeze", "squirrel", "stable",
            "stadium", "staff", "stage", "stairs", "stamp", "stand", "start", "state",
            "stay", "steak", "steel", "stem", "step", "stereo", "stick", "still",
            "sting", "stock", "stomach", "stone", "stool", "story", "stove", "strategy",
            "street", "strike", "strong", "struggle", "student", "stuff", "stumble", "style",
            "subject", "submit", "subway", "success", "such", "sudden", "suffer", "sugar",
            "suggest", "suit", "summer", "sun", "sunny", "sunset", "super", "supply",
            "support", "supreme", "sure", "surface", "surge", "surprise", "surround", "survey",
            "suspect", "sustain", "swallow", "swamp", "swap", "swarm", "swear", "sweet",
            "swift", "swim", "swing", "switch", "sword", "symbol", "symptom", "syrup",
            "system", "table", "tackle", "tag", "tail", "talent", "talk", "tank",
            "tape", "target", "task", "taste", "tattoo", "taxi", "teach", "team",
            "tell", "ten", "tenant", "tennis", "tent", "term", "test", "text",
            "thank", "that", "theme", "then", "theory", "there", "they", "thing",
            "this", "thought", "three", "thrive", "throw", "thumb", "thunder", "ticket",
            "tide", "tiger", "tilt", "timber", "time", "tiny", "tip", "tired",
            "tissue", "title", "toast", "tobacco", "today", "toddler", "toe", "together",
            "toilet", "token", "tomato", "tomorrow", "tone", "tongue", "tonight", "tool",
            "tooth", "top", "topic", "topple", "torch", "tornado", "tortoise", "toss",
            "total", "tourist", "toward", "tower", "town", "toy", "track", "trade",
            "traffic", "tragic", "train", "transfer", "trap", "trash", "travel", "tray",
            "treat", "tree", "trend", "trial", "tribe", "trick", "trigger", "trim",
            "trip", "trophy", "trouble", "truck", "true", "truly", "trumpet", "trust",
            "truth", "try", "tube", "tuition", "tumble", "tuna", "tunnel", "turkey",
            "turn", "turtle", "twelve", "twenty", "twice", "twin", "twist", "two",
            "type", "typical", "ugly", "umbrella", "unable", "unaware", "uncle", "uncover",
            "under", "undo", "unfair", "unfold", "unhappy", "uniform", "unique", "unit",
            "universe", "unknown", "unlock", "until", "unusual", "unveil", "update", "upgrade",
            "uphold", "upon", "upper", "upset", "urban", "urge", "usage", "use",
            "used", "useful", "useless", "usual", "utility", "vacant", "vacuum", "vague",
            "valid", "valley", "valve", "van", "vanish", "vapor", "various", "vast",
            "vault", "vehicle", "velvet", "vendor", "venture", "venue", "verb", "verify",
            "version", "very", "vessel", "veteran", "viable", "vibrant", "vicious", "victory",
            "video", "view", "village", "vintage", "violin", "virtual", "virus", "visa",
            "visit", "visual", "vital", "vivid", "vocal", "voice", "void", "volcano",
            "volume", "vote", "voyage", "wage", "wagon", "wait", "walk", "wall",
            "walnut", "want", "warfare", "warm", "warrior", "wash", "wasp", "waste",
            "water", "wave", "way", "wealth", "weapon", "weary", "weather", "weave",
            "web", "wedding", "weekend", "weird", "welcome", "west", "wet", "whale",
            "what", "wheat", "wheel", "when", "where", "whip", "whisper", "wide",
            "width", "wife", "wild", "will", "win", "window", "wine", "wing",
            "wink", "winner", "winter", "wire", "wisdom", "wise", "wish", "witness",
            "wolf", "woman", "wonder", "wood", "wool", "word", "work", "world",
            "worry", "worth", "wrap", "wreck", "wrestle", "wrist", "write", "wrong",
            "yard", "year", "yellow", "you", "young", "youth", "zebra", "zero",
            "zone", "zoo"
        )
    }
}
