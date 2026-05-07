/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.StringConstant;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
final class GameVersion {
    private GameVersion() {
    }

    // For Minecraft 1.0 rc versions and versions earlier than Alpha 1.0.6,
    // it is difficult to obtain the game version from the JAR.
    // For these versions, we get the version number based on their SHA-1 hash.
    private static final Map<String, String> KNOWN_VERSIONS = Map.<String, String>ofEntries(
            Map.entry("4df7880d26414b400640f0b8e54344df2b66c51a", "1.0.0-rc1"),
            Map.entry("9e04e60eef3fb4657b406dcb3ad5e3a675ecf6af", "1.0.0-rc2-1"),
            Map.entry("6a6b67d34149afc47cf9608b3967582639097df9", "1.0.0-rc2-2"),
            Map.entry("6e54fbe19b7797f3e3a2cb9feb5da41a40926db8", "1.0.0-rc2-3"),
            Map.entry("fe189e91a3e7166d46fad8ce53ba0ce34b4c5f97", "a1.0.5"),
            Map.entry("73f569bf5556580979606049204835ae1a54f04d", "a1.0.5_01"),
            Map.entry("e5838277b3bb193e58408713f1fc6e005c5f3c0c", "a1.0.4"),
            Map.entry("31e9736457ef3e0bfea69c720137a1bd8ba7caae", "a1.0.3"),
            Map.entry("4f9ce27cfc6394af533fde11a90b6a233dd908bf", "a1.0.2_02"),
            Map.entry("7457e763ad81eee1e63628d628647f53806dab7c", "a1.0.2_01"),
            Map.entry("02c57723da508aab36455782904bfd6e3e1023e6", "a1.0.1_01"),
            Map.entry("88c1931650b0e5be349017e124a7785a745111e9", "inf-20100630-2"),
            Map.entry("121fff417950ad72005ca4d882ca6269e874547b", "inf-20100630-1"),
            Map.entry("eb50bce3cb542488b3039aa0f4c3c0ec7595ab24", "inf-20100629"),
            Map.entry("4d31259a71c5886b987b9eca6034ca5552079eed", "inf-20100627"),
            Map.entry("d9fc6416186e1454945ab135f37c730c7d2c1adc", "inf-20100625-2"),
            Map.entry("990b531a26ae8e475032915938763c12cdb2dcf9", "inf-20100625-1"),
            Map.entry("644c050e846035e06a6637bffa2afee1e5769c8c", "inf-20100624"),
            Map.entry("d3eb1dce5a6c86dd0d6483ba56223276dcf32c30", "inf-20100617-3"),
            Map.entry("06641eca013fe5032a5f1a9d1289599f0970a735", "inf-20100617-2"),
            Map.entry("89eab2c1a353707cc00f074dffba9cb7a4f5e304", "inf-20100618"),
            Map.entry("47518a623da068728b50b4b53436dea4621b7bf8", "inf-20100615"),
            Map.entry("421318a554f17463a56a271d08e9597941d066d9", "inf-20100611"),
            Map.entry("a9efb36c142bf835d3d410150856dc9ceeaae81b", "inf-20100608"),
            Map.entry("7bbf38d53dd47753af266be4e1c5865342a26974", "inf-20100607"),
            Map.entry("27010a5137abd2c8d8df85e99c14f5406ec197b3", "inf-20100420"),
            Map.entry("a91c9d8e0184eda610213b1a5425fbfa078cb191", "inf-20100415"),
            Map.entry("86dd3b1558352b38d4d15c7ec51b9131bd7aed4b", "inf-20100414"),
            Map.entry("7b39167f14d9f0ce7af6819433856be7b82d2412", "inf-20100413"),
            Map.entry("a74c8ee1ecd57999e242952697bbde6cc0904f99", "inf-20100330"),
            Map.entry("47b1b32430a211520993552ba0a5e00c1af44724", "inf-20100327"),
            Map.entry("99da3b55b4db292faca59824e3ec76bf53a7eae6", "inf-20100325"),
            Map.entry("2c89471a81858d37ab0b01e042131878b6853b38", "inf-20100321"),
            Map.entry("7f1c48fc6d61dd0cbfd41b84fb0b0a22944aa02c", "inf-20100320"),
            Map.entry("ad7b3cd706098ac05c7dba61dacb40bafcd47db6", "inf-20100316"),
            Map.entry("65a00a10001978538ab8eef1a2533f47d4ecbe23", "inf-20100313"),
            Map.entry("801ce486bb7fd1b43a56bc5d226dfb1370c08678", "in-20100223"),
            Map.entry("af3d7f95ca75e130a9c5c74be0a9c09600a15686", "in-20100219"),
            Map.entry("2ba9e9a2bdac1e8af6a36819e9bb01375889b078", "in-20100218"),
            Map.entry("dcbe38d0e4ac2caec7e5c0f9ebcb0ec9179dcdff", "in-20100214-2"),
            Map.entry("e6bb9306dab60626ba6ffd24fc9742fd272f5acb", "in-20100214-1"),
            Map.entry("f1ae7e37e52b33753b35402e581eb65dc5bba877", "in-20100212-2"),
            Map.entry("5275aaf68d6388ef8278b575e95ae83ad641fe3e", "in-20100212-1"),
            Map.entry("fa8525be5612d00f6001be7d4cdb764b66e88f9d", "in-20100207-2"),
            Map.entry("054e3d3f4e2c0463f80aa323767e018e6c23c1cd", "in-20100207-1"),
            Map.entry("049b002cdd164e5c5e9b78780b12ab4dc2e80120", "in-20100206-2103"),
            Map.entry("b2abb22e001abf01ca7555ced5d6024350955d70", "in-20100203"),
            Map.entry("38d4df5132077ac60f0bdf67564f5fff4ee309e2", "in-20100201-3"),
            Map.entry("1f2ca31fc761207bcabc07f0cf4b725a9a3286e4", "in-20100201-2"),
            Map.entry("c871e820d5356b88b3ad854789162f8b9227c80c", "in-20100130"),
            Map.entry("03b858d31c090b629f406aa1d548ac7b25341f02", "in-20100129"),
            Map.entry("3f2418f906d438b26ae6c9dbbadf3942f5845504", "in-20100128-2304"),
            Map.entry("baf0c7b1e231f0984e1c35e27f38eea2743f8ee2", "in-20100125-2"),
            Map.entry("2cd03bcfc26c95bcf31b5d5e1d4dda7dc071ca6a", "in-20100125-1"),
            Map.entry("a0b58472ebf12f7e562b09b8a51dcb4cacc57005", "in-20100111-1"),
            Map.entry("38958105bfe0f7064b3c4996905cb6978d4d4b0b", "in-20100105"),
            Map.entry("3161652a6835c61817fda6fe13245c57528ed418", "in-20091231-2"),
            Map.entry("94ee2e7aa7d093fa8dfc684baa8bd8afe002580f", "in-20091223-2"),
            Map.entry("54622801f5ef1bcc1549a842c5b04cb5d5583005", "c0.30_01c"),
            Map.entry("51bc951530207b538596941a6f353f87dfc24233", "c0.30-2"),
            Map.entry("619ea74c6d0ae5c0125d1e31e299105e100139ab", "c0.30-1"),
            Map.entry("6a6f92b691f9d6b7ca991a6db8a1cfc6e319815b", "c0.29_02"),
            Map.entry("bb5e7f1c231f45fd630f30a75570937c103f5b55", "c0.29_01"),
            Map.entry("7ccde270abacd028d3618be99537ccf7071a605b", "c0.28_01"),
            Map.entry("aff4060249dd6152012218e120d7aad5e758de83", "c0.27_st"),
            Map.entry("349630cb1b895335c38b499f84dc28d9f8a38513", "c0.25_05_st"),
            Map.entry("0b387d2087edda894fae4af00de5ac202dbffa7c", "c0.24_st_03"),
            Map.entry("85159cea8663ed720be88ca0ee008a5830b0829a", "c0.0.22a_05"),
            Map.entry("83b6483feb88136b6b4662b553d8f80f5f88efa5", "c0.0.21a"),
            Map.entry("c2f8fddde4691d7c567c0c049ad4d03eb6b9e61c", "c0.0.20a_01"),
            Map.entry("e2b248f1013933af9f801729418409fb7198de1b", "c0.0.19a_06-2"),
            Map.entry("a78468abd491d6c661c000f60d6270a692ba4710", "c0.0.18a_02"),
            Map.entry("ca840460a6589552c9d1978ca121bf3e7c16a010", "c0.0.17a"),
            Map.entry("741eb3f84097fdcc0327230e018a0f8cd39addfb", "c0.0.16a_02"),
            Map.entry("936d575b1ab1a04a341ad43d76e441e88d2cd987", "c0.0.13a"),
            Map.entry("e8aa74a5bee547097375d44ffb2e407b2ea8ee4d", "c0.0.14a_08"),
            Map.entry("b9884f960f2b28a36b34db3447963f1ff4058aa4", "c0.0.23a_01"),
            Map.entry("7ba9e63aec8a15a99ecd47900c848cdce8a51a03", "c0.0.13a_03"),
            Map.entry("501ea8a6274faffe0144d3b24ed56797ce0765ff", "c0.0.12a_03"),
            Map.entry("3a799f179b6dcac5f3a46846d687ebbd95856984", "c0.0.11a"),
            Map.entry("6323bd14ed7f83852e17ebc8ec418e55c97ddfe4", "rd-161348"),
            Map.entry("b100be8097195b6c9112046dc6a80d326c8df839", "rd-160052"),
            Map.entry("12dace5a458617d3f90337a7ebde86c0593a6899", "rd-132328"),
            Map.entry("393e8d4b4d708587e2accd7c5221db65365e1075", "rd-132211")
    );

    private static Optional<String> getVersionFromJson(InputStream versionJson) {
        try {
            Map<?, ?> version = JsonUtils.fromNonNullJsonFully(versionJson, Map.class);
            String id = (String) version.get("id");
            if (id != null && id.contains(" / "))
                id = id.substring(0, id.indexOf(" / "));
            return Optional.ofNullable(id);
        } catch (IOException | JsonParseException | ClassCastException e) {
            LOG.warning("Failed to parse version.json", e);
            return Optional.empty();
        }
    }

    private static Optional<String> getVersionOfClassMinecraft(InputStream bytecode) throws IOException {
        final String constantPrefix = "Minecraft Minecraft ";
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);
        for (StringConstant constant : pool.list(StringConstant.class)) {
            String value = constant.get();
            if (value.startsWith(constantPrefix)) {
                return Optional.of(value.substring(constantPrefix.length()));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getVersionFromClassMinecraftServer(InputStream bytecode) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);

        List<String> list = StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
                .map(StringConstant::get)
                .toList();

        int idx = -1;

        for (int i = 0; i < list.size(); ++i)
            if (list.get(i).startsWith("Can't keep up!")) {
                idx = i;
                break;
            }

        Pattern pattern = Pattern.compile(".*[0-9].*");
        for (int i = idx - 1; i >= 0; --i)
            if (pattern.matcher(list.get(i)).matches())
                return Optional.of(list.get(i));

        return Optional.empty();
    }

    public static Optional<String> minecraftVersion(Path file) {
        if (file == null || !Files.isRegularFile(file))
            return Optional.empty();

        try (var gameJar = new ZipFile(file.toFile())) {
            ZipEntry versionJson = gameJar.getEntry("version.json");
            if (versionJson != null) {
                Optional<String> result = getVersionFromJson(gameJar.getInputStream(versionJson));
                if (result.isPresent())
                    return result;
            }

            ZipEntry minecraft = gameJar.getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null) {
                try (InputStream is = gameJar.getInputStream(minecraft)) {
                    Optional<String> result = getVersionOfClassMinecraft(is);
                    if (result.isPresent()) {
                        String version = result.get();
                        // For Minecraft 1.0 rc1/rc2-1/rc2-2, this value is "RC1"
                        // For Minecraft 1.0 rc2-3, this value is "RC2"
                        if (!version.equals("RC1") && !version.equals("RC2")) {
                            if (version.startsWith("Beta ")) {
                                result = Optional.of("b" + version.substring("Beta ".length()));
                            } else if (version.startsWith("Alpha v")) {
                                result = Optional.of("a" + version.substring("Alpha v".length()));
                            }
                            return result;
                        }
                    }
                }
            }

            ZipEntry minecraftServer = gameJar.getEntry("net/minecraft/server/MinecraftServer.class");
            if (minecraftServer != null) {
                try (InputStream is = gameJar.getInputStream(minecraftServer)) {
                    return getVersionFromClassMinecraftServer(is);
                }
            }
        } catch (IOException ignored) {
        }

        try {
            String digest = DigestUtils.digestToString("SHA-1", file);
            return Optional.ofNullable(KNOWN_VERSIONS.get(digest));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
