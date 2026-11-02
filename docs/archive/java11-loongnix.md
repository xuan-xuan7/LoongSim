# 历史归档：Java 11 / 旧世界 Loongnix

> 本文保留原有说明。示例版本和启动脚本对应历史环境；当前 master 的通用运行方式见[开发文档](../development/README.md)。

此版本以 Java 11 字节码编译，需要 Java 11 或更高版本运行。Java 8 不再兼容。

直接运行：

```sh
cd logisim
chmod +x run-loongsim.sh
./run-loongsim.sh
```

重新编译：

```sh
java -version
javac -version
./gradlew clean shadowJar --no-daemon
```

生成文件：

```text
build/libs/LoongSim-1.0.1-java11-all.jar
```

该 JAR 不包含 CPU 原生代码，因此能在 x86、ARM、LoongArch 上共用；运行它的 Java
虚拟机必须与所在系统的 ABI 匹配。旧世界 Loongnix 必须使用旧世界原生 Java 11。
