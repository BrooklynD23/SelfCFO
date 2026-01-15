# 03: Development Tooling

## Overview

Configure development tools for code quality, formatting, and documentation generation.

---

## Implementation Steps

### Step 1: Configure ktlint

```kotlin
// build.gradle.kts (root)
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.1.1")
        android.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)

        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}
```

Create `.editorconfig`:
```ini
# .editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
ktlint_code_style = android_studio
max_line_length = 120

[*.md]
trim_trailing_whitespace = false
```

### Step 2: Configure detekt

```kotlin
// build.gradle.kts (root)
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("$rootDir/config/detekt/baseline.xml")
    }
}
```

Create detekt configuration:
```yaml
# config/detekt/detekt.yml
build:
  maxIssues: 0
  excludeCorrectable: false

config:
  validation: true
  warningsAsErrors: true

complexity:
  LongMethod:
    threshold: 60
  LongParameterList:
    functionThreshold: 8
    constructorThreshold: 10
  ComplexCondition:
    threshold: 4

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
  VariableNaming:
    variablePattern: '[a-z][a-zA-Z0-9]*'

style:
  MagicNumber:
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
      - '100'
    ignorePropertyDeclaration: true
  MaxLineLength:
    maxLineLength: 120
  WildcardImport:
    active: true
```

### Step 3: Configure Pre-commit Hooks

Create pre-commit hook script:
```bash
#!/bin/sh
# .git/hooks/pre-commit

echo "Running ktlint..."
./gradlew ktlintCheck --daemon

if [ $? -ne 0 ]; then
  echo "ktlint check failed. Please fix the issues before committing."
  exit 1
fi

echo "Running detekt..."
./gradlew detekt --daemon

if [ $? -ne 0 ]; then
  echo "detekt check failed. Please fix the issues before committing."
  exit 1
fi

echo "All checks passed!"
exit 0
```

Create setup script:
```bash
#!/bin/bash
# scripts/setup-hooks.sh

HOOKS_DIR=".git/hooks"
PRE_COMMIT="$HOOKS_DIR/pre-commit"

# Create hooks directory if it doesn't exist
mkdir -p "$HOOKS_DIR"

# Copy pre-commit hook
cat > "$PRE_COMMIT" << 'EOF'
#!/bin/sh
echo "Running ktlint..."
./gradlew ktlintCheck --daemon
if [ $? -ne 0 ]; then
  echo "ktlint check failed."
  exit 1
fi

echo "Running detekt..."
./gradlew detekt --daemon
if [ $? -ne 0 ]; then
  echo "detekt check failed."
  exit 1
fi

echo "All checks passed!"
exit 0
EOF

chmod +x "$PRE_COMMIT"
echo "Pre-commit hook installed successfully!"
```

### Step 4: Configure Dokka

```kotlin
// build.gradle.kts (root)
plugins {
    id("org.jetbrains.dokka") version "1.9.10"
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}

tasks.register<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    outputDirectory.set(file("$buildDir/dokka"))
}
```

```kotlin
// shared/build.gradle.kts
tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            displayName.set("Common")
            platform.set(org.jetbrains.dokka.Platform.common)
        }
        named("androidMain") {
            displayName.set("Android")
            platform.set(org.jetbrains.dokka.Platform.jvm)
        }
        named("desktopMain") {
            displayName.set("Desktop")
            platform.set(org.jetbrains.dokka.Platform.jvm)
        }
    }
}
```

### Step 5: IDE Configuration Files

```xml
<!-- .idea/codeStyles/Project.xml -->
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <JetCodeStyleSettings>
      <option name="CODE_STYLE_DEFAULTS" value="KOTLIN_OFFICIAL" />
    </JetCodeStyleSettings>
    <codeStyleSettings language="kotlin">
      <option name="CODE_STYLE_DEFAULTS" value="KOTLIN_OFFICIAL" />
    </codeStyleSettings>
  </code_scheme>
</component>
```

```xml
<!-- .idea/inspectionProfiles/Project_Default.xml -->
<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Project Default" />
    <inspection_tool class="UnusedImport" enabled="true" level="WARNING" enabled_by_default="true" />
  </profile>
</component>
```

---

## Acceptance Criteria

- [ ] ktlint runs without errors: `./gradlew ktlintCheck`
- [ ] detekt runs without errors: `./gradlew detekt`
- [ ] Pre-commit hooks installed and working
- [ ] Dokka generates documentation: `./gradlew dokkaHtmlMultiModule`
- [ ] IDE code style configured
- [ ] Setup script works for new developers

---

## Dependencies

- ktlint 1.1.1+
- detekt 1.23.4+
- Dokka 1.9.10+

---

## Estimated Complexity

**Low** - Standard tooling configuration.

---

## Verification Commands

```bash
# Run all checks
./gradlew check

# Format code
./gradlew ktlintFormat

# Generate docs
./gradlew dokkaHtmlMultiModule

# Install hooks
./scripts/setup-hooks.sh
```
