# Hello Minecraft! Launcher [![Build Status](https://travis-ci.org/huanghongxun/HMCL.svg?branch=master)](https://travis-ci.org/huanghongxun/HMCL)
开源协议为GPL v3, 详情参见http://www.gnu.org/licenses/gpl.html

## 介绍

HMCL是一个Minecraft启动器，支持Mod管理，游戏定制，自动安装，整合包制作，界面主题定制等功能。
但是本项目的代码不够完善，希望有更多的人能加入HMCL的开发。

## 贡献
如果你很想为本项目贡献代码，这里有一些要求：
* 开发环境为Netbeans 8.1
* 编译器为Java 1.8，通过retrolambda兼容Java 1.7，所以请不要使用Java 8的新API（如Stream）
* 请不要修改`gradle`文件

## 代码
* 包 `org.jackhuang.hellominecraft.util` 为HMCL和HMCSM的共用工具代码
* 包 `org.jackhuang.hellominecraft.launcher` 包含了HMCL的界面以及逻辑代码
* 包 `org.jackhuang.hellominecraft.launcher.core` 为HMCL的启动核心
* 包 `org.jackhuang.hellominecraft.launcher.api` 是HMCL为了便于定制提供的API，暂不支持加载插件
* 包 `org.jackhuang.hellominecraft.svrmgr` 为HMCSM的所有代码
* 文件夹 `HMCUtils/src/main/resources/org/jackhuang/hellominecraft/lang` 包含了HMCL和HMCSM使用的语言文件

由于包树已经相当清晰，因此不再赘述各包各类的用途。

## 注意事项
* 包 `org.jackhuang.hellominecraft.util.logging` 包含了经过精简的Apache License 2.0的Log4j项目的代码
* 包 `com.google.gson` 为Google Gson项目Apache License 2.0的代码
* 包 `org.jackhuang.hellominecraft.lookandfeel.ui` 包含了Sun Microsystems的NimbusLookAndFeel项目的部分LGPL代码
* 所有Pull Request提交的代码均会被重写
* 本项目的开源协议是GPL v3，因此包含LGPL和Apache License 2.0的代码是没有法律问题的
