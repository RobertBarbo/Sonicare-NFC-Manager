# Third-party notices and research acknowledgements

Sonicare NFC Manager does not declare any third-party runtime library dependency beyond the Kotlin standard library added by the Kotlin Android toolchain.

## Kotlin

The project is written in Kotlin and uses Kotlin 2.0.21 tooling and the Kotlin standard library.

- Copyright © 2010–2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
- License: Apache License 2.0.
- Project: <https://github.com/JetBrains/kotlin>

The resolved release runtime classpath also includes **JetBrains Java Annotations 13.0** through the Kotlin standard library.

- Copyright © JetBrains s.r.o.
- License: Apache License 2.0.
- Project: <https://github.com/JetBrains/java-annotations>

## Sonicare NFC password research

The compatible-tag password calculation is based on the publicly documented reverse-engineering method published by **@atc1441**:

- <https://gist.github.com/atc1441/41af75048e4c22af1f5f0d4c1d94bb56>

The following project was reviewed as an additional compatibility reference and also credits @atc1441:

- **nicjes/SonicareGenerator**: <https://github.com/nicjes/SonicareGenerator>

During review on 2026-11-12, neither linked source page displayed an explicit software license. They are therefore credited as research and compatibility references; no source file or binary from either project is included as a dependency in this repository. This notice does not claim that attribution creates a license grant for third-party code.

The implementation in `SonicareProtocol.kt` is not a verbatim or line-for-line copy of either reference. It expresses the documented CRC method in Kotlin with project-specific validation, data handling and raw-byte output. The mathematical constants and operation order are retained because they define compatibility with supported tags.

## Build tooling

The Gradle wrapper, Gradle, the Android Gradle Plugin and related Android build tools are used to build the application and are not presented as original work of this project. Their own license notices remain applicable.
