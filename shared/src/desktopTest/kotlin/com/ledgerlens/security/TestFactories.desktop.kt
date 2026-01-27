package com.ledgerlens.security

actual fun createFileEncryption(): FileEncryption = AesGcmFileEncryption()

actual fun createPlatformKeystore(): PlatformKeystore = DesktopPlatformKeystore()

