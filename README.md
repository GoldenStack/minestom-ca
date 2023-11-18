# minestom-ca

[![license](https://img.shields.io/github/license/GoldenStack/minestom-ca?style=for-the-badge&color=dd2233)](LICENSE)
[![standard-readme compliant](https://img.shields.io/badge/readme%20style-standard-brightgreen.svg?style=for-the-badge)](https://github.com/RichardLitt/standard-readme)

Implementing vanilla Minecraft with cellular automata.

Many vanilla features (redstone, water, grass, furnaces, etc.) can be implemented declaratively with cellular automata.
This involves essentially 'describing' behaviour, and the server will implement the cellular automata described.

Benefits of this include:
- Much simpler to implement features
- Less code for each feature
- Extremely parallel world processing
- Abstracts away the idea of chunks and chunk sections
- More emergent gameplay (features can be described and may interact)
- Reduces bugs by focusing on description instead of implementation

--- 

## Table of Contents
- [Install](#install)
- [Usage](#usage)
- [Contributing](#contributing)
- [License](#license)

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