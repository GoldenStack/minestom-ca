# minestom-ca

[![license](https://img.shields.io/github/license/GoldenStack/minestom-ca?style=for-the-badge&color=dd2233)](LICENSE)
[![standard-readme compliant](https://img.shields.io/badge/readme%20style-standard-brightgreen.svg?style=for-the-badge)](https://github.com/RichardLitt/standard-readme)

Implementing vanilla Minecraft with cellular automata.

Many vanilla features (redstone, water, grass, furnaces, etc.) can be implemented declaratively with cellular automata.
This involves essentially 'describing' behaviour, and the server will implement the cellular automata described.

The current goal is to:
- Create a custom language for defining rules
- Parse this language into an intermediate state (similar to an AST)
- Interpret this state a number of ways: via the GPU, state tracking, etc.

Benefits of this include:
- Much simpler to implement features
- Less code for each feature
- Extremely parallel world processing
- Abstracts away the idea of chunks and chunk sections
- More emergent gameplay (features can be described and may interact)
- Reduces bugs by focusing on description instead of implementation

--- 

## Table of Contents
- [Introduction](#introduction)
- [Install](#install)
- [Usage](#usage)
- [Contributing](#contributing)
- [License](#license)


## Introduction
Minestom-ca rules are written in our custom language. Here's an example:
```
state=#dirt & up@state=#air -> state=#grass
state=#grass & up@state!=#air -> state=#dirt
```
On the left, there are two rules, joined with an ampersand (`&`). Then, there's an arrow (`->`) that indicates the
changes that should be made if the condition on the left is true.

This means that, if there is a `dirt` block and the block in the `up` direction is `air`, change the block to `grass`.
This can be read as a rule that grows all uncovered grass.

### State
Each block has a list of palette indices. When writing rules, you can just use the names.
For example, all blocks have a `state` palette, because each block must have a state.

However, you can also add other states: like `age`. A tree could have a rule that decreases the age value once per tick,
and grows once the age is zero. This would look something like:
```
state=#sapling & age>0 -> age--
state=#sapling & age=0 -> state=#log
```
This code turns the sapling into a log, but you could make it actually grow instead.

### Rule range
Each rule can only read the data of blocks bordering it. This means that changes can only propagate at 1 block / tick.

This is an intentional limitation of this system.

For example, redstone cannot be modeled with vanilla parity, as changes can only travel across redstone at the speed of
light. However, you could simply tick each rule faster, and artificially slow down the other rules.

## Install

To install, simply add the library via [JitPack](https://jitpack.io/#GoldenStack/minestom-ca/-SNAPSHOT):

Details for how to add this library with other build tools (such as Maven) can be found on the page linked above.
``` kts
repositories {
    // ...

    maven("https://jitpack.io")
}

dependencies {
    // ...
    implementation("com.github.GoldenStack:minestom-ca:VERSION-HERE")
}
```

## Usage

TODO

## Contributing

Found a bug? Explain it clearly in a new issue.

Fixed a bug? Feel free to open a pull request.

Adding a feature? Make sure to check with a maintainer that it's actually wanted.

All contributions made and submitted are licensed under [MIT](LICENSE).

## License

All code in this project is licensed under [MIT](LICENSE)