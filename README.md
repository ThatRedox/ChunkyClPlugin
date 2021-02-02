
![Java CI with Gradle](https://github.com/alexhliu/ChunkyClPlugin/workflows/Java%20CI%20with%20Gradle/badge.svg)

# ChunkyCL
ChunkyCL is a plugin to the Minecraft Pathtracer [Chunky](https://github.com/chunky-dev/chunky) which harnesses the power of the GPU with OpenCL 1.2 to accelerate rendering.
It is currently a work in progress and does not support many features such as non-cube blocks and biome tinting. The core renderer itself is still under development
so render results may change drastically between versions.

## Installation

Download the [latest plugin build by selecting the most recent successful workflow and downloading the ChunkyClPlugin Artifact](https://github.com/alexhliu/ChunkyClPlugin/actions?query=is%3Asuccess+event%3Apush+branch%3Amaster) and extract the zip file.
In the Chunky Launcher, expand `Advanced Settings` and click on `Manage plugins`. In the `Plugin Manger` window click on `Add` and select the `.jar` file in the extracted
zip file. Click on `Save` and start Chunky as usual.

## Compaitability

* Not compaitable with the Denoising Plugin.
