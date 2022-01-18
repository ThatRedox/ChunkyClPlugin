[![Stable](https://github.com/alexhliu/ChunkyClPlugin/actions/workflows/stable.yml/badge.svg)](https://github.com/alexhliu/ChunkyClPlugin/actions/workflows/stable.yml)
[![Development](https://github.com/alexhliu/ChunkyClPlugin/actions/workflows/development.yml/badge.svg)](https://github.com/alexhliu/ChunkyClPlugin/actions/workflows/development.yml)


# ChunkyCL

ChunkyCL is a plugin to the Minecraft Path-tracer [Chunky](https://github.com/chunky-dev/chunky) which harnesses the power of the GPU with OpenCL 1.2+ to accelerate rendering.
It is currently a work in progress and does not support many features such as non-cube blocks and biome tinting. The core renderer itself is still under development
so render results may change drastically between versions.

## Downloads
* [Latest mostly stable build](https://nightly.link/ThatRedox/ChunkyClPlugin/workflows/stable/stable/ChunkyClPlugin.zip)
* [Latest development build](https://nightly.link/ThatRedox/ChunkyClPlugin/workflows/development/master/ChunkyClPlugin.zip)

<sub><sup>Note: Even if the build is failing, the link will link to the latest successful build. This will work with some older snapshots of Chunky.</sup></sub>

## Installation

### Note: The latest version requires Chunky `2.4.0` or `2.4.1`
Download the latest plugin build and extract it. In the Chunky Launcher, expand `Advanced Settings` and click on `Manage plugins`. In the `Plugin Manager` window click on `Add` and select the `.jar` file in the extracted zip file. Click on `Save` and start Chunky as usual.

![image](https://user-images.githubusercontent.com/42661490/116319916-28ef2580-a76c-11eb-9f93-86d444a349fd.png)

Select `ChunkyCL` as your renderer for the scene in the `Advanced` tab.

![image](https://user-images.githubusercontent.com/42661490/122492084-fc040580-cf99-11eb-9b08-b166dc25db41.png)

## Performance

Rough performance with a RTX 2070 is around 40 times that of the traditional CPU renderer as of 2021-09-06.

Some settings have been added to improve render performance.
* Indoor scenes should disable sunlight under `Lighting`
* Draw depth may be adjusted under `Advanced`
* Draw entities may be unchecked under `Advanced`
* OpenCL Device selector under `Advanced`

## Compatibility

* Not compatible with the Denoising Plugin.
