## Contribute

To maintain the coherence of the HMCL project, it's imperative to synchronize updates to both on-site and off-site resources.

If you want to modify the resources, please execute `./gradlew computeDynamicResources` in your command line. Then, update the files in `build` directory to [here](https://github.com/HMCL-dev/HMCL-docs/tree/main/assets/hmcl/data-json/).

To confirm the success of the distribution, you can check by visiting [this link](https://docs.hmcl.net/assets/hmcl/data-json/dynamic-remote-resources.json).

## Structure

The update JSON of dynamic remote resource system should be a three-layer map, which stands for the namespace, name and version keys.
For each dynamic remote resource under the three-layer map, it should contain these values.

- `urls`: The download URLs of this resource. HMCL would try these download URLs one by one from the top to the bottom.
- `local_path`: The path of this resource in the HMCL repository. This value would be removed while executing `computeDynamicResources`, as it is only used for verifying the hashcode.
- `sha1`: The hashcode of the resource data. DO REMEMBER, these files should use LF as the line separator.

## Items

Currently, HMCL have these dynamic remote resources.

- `translation:mod_data:1`: The Chinese translation of the mod names.
- `translation:modpack_data:1`: The Chinese translation of the modpack names.