# AndroidAsync
迁移自：https://github.com/koush/AndroidAsync


1. http请求的时候，编码内容不识别ContentType
https://github.com/koush/AndroidAsync/issues/624
2. 关于urlEncoding编码规范，可以将空格翻译为"+"，本框架似乎没有反解"+"(似乎是hermesAdmin的bug，待定)
3. AndroidAsync目前应该要进行一次不兼容的大版本升级，jdk由1.7升级到1.8，但是Hermes系列工具链需要保证在低版本的手机上面运行。所以将jdk版本回退到1.7。并将1.8的语法进行降级

在未来HermesAgent使用独立分支的http服务器框架，不在follow AndroidAsync的版本发布