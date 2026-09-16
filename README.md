# Aidsfuscator v2.x

Aidsfuscator is a Java bytecode obfuscator that aims to become one of, if not the best, free obfuscators.

<br>

Join the [Discord server](https://discord.gg/4JGANqEZsK)!

</br>

### IF YOU'RE PLANNING TO CONTRIBUTE, SCROLL DOWN AND READ THE README!

## Features

- Trimming
- Name obfuscation (optional aggressive overloading!)
- Control flow flattening
- Control flow shuffling
- `LocalVariableTable` clearing
- `LineNumberTable` mutation
- Method salting
- Class salting
- Integer encryption
- String encryption (with anti-tampering and concatenation obfuscation)
- Reference obfuscation (fields and methods)
- Custom dictionary support
- Fat JAR support
- CLI
- Configuration system
- Exclusion system with annotations
- Exclusion preset system
- Automatic `fabric.mod.json` handling
- Annotations API

## Why?

Over the two years I have spent working with Java bytecode, I have noticed that there are not many good, free Java bytecode obfuscators available, so I decided to make one myself. It is also a passion project.

## Why v2?

I created a poll on the [Aidsfuscator/Cryptics Discord server](https://discord.gg/4JGANqEZsK) asking whether anyone wanted me to rewrite Aidsfuscator v1. The majority said yes, so that is what motivated me to start this project.

## How to Use

- Download the ZIP file from [the releases page](https://github.com/LvStrnggg/aidsfuscator/releases).
- Extract the ZIP file.
- Your `config.json` and `exclusions.json` files should remain in your workspace folder; everything else can stay outside it.
- Run the obfuscator with Java 25: `java -jar aidsfuscator.jar --config=config.json --exclusions=exclusions.json`

### Default Structure

```text
aidsfuscator.jar
workspace
|- aidsfuscator-api.jar
|- config.json
|- initOrder.json
|- references.json
\- exclusions.json
```

### What Not to Do

- Do not run it with `java -jar aidsfuscator --config=workspace/config.json --exclusions=workspace/exclusions.json`. Aidsfuscator automatically prefixes `workspace/` to any workspace item that is not the libraries folder or the input file.

### Aidsfuscator API

Starting with v2.9.0, Aidsfuscator includes an API JAR file with several annotations. This JAR can be added to your project's dependencies, allowing you to annotate methods, fields, and classes to exclude them from obfuscation. These annotations are removed by Aidsfuscator's post-processor.

## Using the Obfuscator

### Exclusions and Inclusions

You can add exclusions and inclusions using the following format in the exclusions file:

```json
{
  "token": {
    "class": [
      "example/Exclusion",
      "!example/Inclusion"
    ],
    "field": [
      "example/Exclusion.excludedMethod Ljava/lang/String;",
      "!example/Inclusions.includedMethod Ljava/lang/String;"
    ],
    "method": [
      "example/Exclusion.excludedMethod(IJZBDFSCLjava/lang/String;)V",
      "!example/Inclusion.includedMethod(IJZBDFSCLjava/lang/String;)V"
    ],
    "annotation": [
      "example/ExampleAnnotation"
    ]
  }
}
```

Replace `token` with any of the following:

- `global` (Excludes a class globally from all obfuscation. Applies to classes only.)
- `renameClass` (Excludes a class from being renamed. Applies to classes only.)
- `renameField` (Excludes a field from being renamed. Applies to classes and fields.)
- `renameMethod` (Excludes a method from being renamed. Applies to classes and methods.)
- `localNames` (Excludes the LVT (Local Variable Table) from having its names cleared. Applies to classes and methods.)
- `lineNumbers` (Excludes the LNT (Line Number Table) from being obfuscated. Applies to classes and methods.)
- `trim` (Excludes a specific member from being trimmed. Applies to classes, fields, and methods.)
- `methodSalting` (Excludes a method from being salted. Applies to classes and methods.)
- `classSalting` (Excludes a class from being salted. Applies to classes only.)
- `referenceObfuscate` (Excludes a method from having its references obfuscated. Applies to classes and methods.)
- `fixConstants` (Excludes a field from having its value moved to the static initializer. Applies to classes and fields.)
- `integerEncrypt` (Excludes a class or method from having its integer values encrypted. Applies to classes and methods.)
- `stringEncrypt` (Excludes a class or method from having its string literals encrypted. Applies to classes and methods.)
- `controlFlowFlatten` (Excludes a method from having its control flow flattened into a switch. Applies to classes and methods.)
- `controlFlowShuffle` (Excludes a method from having its control flow shuffled. Applies to classes and methods.)

If you want to exclude every method named `test`, with any parameters and a `void` return type, from control flow flattening and integer encryption, your exclusions file should look like this:

```json
{
  "controlFlowFlatten": {
    "method": [
      "*.test(*)V"
    ]
  },
  "integerEncrypt": {
    "method": [
      "*.test(*)V"
    ]
  }
}
```

### Class Initialization Order

Class initialization order allows you to strengthen class salts by specifying pairs of classes that are initialized in a specific order. The `initOrder.json` file is a JSON array of JSON arrays, with exactly two classes in each inner array.

For example, if you are sure that `pkg.Class2` is initialized by `pkg.Class1` first and you add it to the file, your file should look like this:

```json
[
  ["pkg.Class1", "pkg.Class2"]
]
```

You can add multiple class pairs and chain them, but you must be certain that the order is correct. Otherwise, incorrect values may be output.

### Reference Obfuscation Inclusions

The reference obfuscation inclusions file specifies reference obfuscation candidates. It uses the same matching system as the exclusions file.

#### Methods

- Any method named `test` in any class, with any parameters and any return type:

  `*.test(*)*`

- Any method named `test` in any class, with no parameters and a `void` return type:

  `*.test()V`

- Any method named `test` in class `pkg.TestClass`, with a `long` first parameter and a `java.lang.String` return type:

  `pkg/TestClass.test(J*)Ljava/lang/String;`

- Any method named `test` in any class ending with `subpkg.TestClass`, with a `boolean` last parameter and an `int` return type:

  `*/subpkg/TestClass.test(*Z)I`

- Any method named `test` in any class under a package called `pkg`, with any parameters and any return type (shortened):

  `*/pkg/*.test(*`

#### Fields

- Any field named `test` in any class, with any type:

  `*.test *`

- Any field named `test` in any class, with a `boolean` type:

  `*.test Z`

- Any field named `test` in class `pkg.TestClass`, with a `java.lang.String` type:

  `pkg/TestClass.test Ljava/lang/String;`

- Any field named `test` in any class ending with `subpkg.TestClass`, with an `int` type:

  `*/subpkg/TestClass.test I`

- Any field named `test` in any class under a package called `pkg`, with any type:

  `*/pkg/*.test *`

Keep in mind that, for fields, you must separate the type from the name with a space.

## Contributing

Since I have received some pull requests that do not meet my expectations, I want to avoid wasting other people's time. Therefore:

When contributing, I expect bug fixes rather than new features. I can decide for myself which features are good and which are not.

<div>
  If you're thinking about adding a feature, please contact me on Discord at `lvstrng` to discuss it. Alternatively, create an issue as a suggestion describing what you would like to see added to the obfuscator. One good example was when, instead of forking the repository, writing the transformer, and creating a pull request, someone created an issue suggesting Zelix-style control flow in the obfuscator. I told the user that this was probably unnecessary because the current control flow obfuscation already does its job. This user avoided wasting a great deal of time on something I would not have agreed with anyway.
</div>

### TL;DR

If you're thinking about adding new features to the obfuscator, contact me on Discord or create a suggestion. Otherwise, please only submit bug fixes.
