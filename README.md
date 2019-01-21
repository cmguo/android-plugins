# android-plugins
Android 应用的插件管理框架

# 功能点：
* 插件自动加载，搜索下列目录：
1. 内部cache目录（如：/data/data/dx.android.plugins.demo/cache/plugins/）
2. 外部cache目录（如：/sdcard/Android/data/dx.android.plugins.demo/cache/plugins/）
3. Usb存储（如：/mnt/usb1/MyUDisk/plugins/，自动发现U盘）
4. 应用APK内置目录（如：内置在assets/plugins/目录）
* 使用插件，可以在插件中包含：
1. Java类（一般通过工厂模式解耦）
2. JNI库（自动解压jni so libs）
3. 资源（插件内部使用PluginContext代替普通Context）
4. 皮肤（与被覆盖资源有相同的名称，支持文字、图片、颜色、字体等等）
* 插件管理：
1. 插件可以延迟启动（插件首次启动需要odex优化，解压so libs等，比较耗时，通过延迟启动减少用户等待）
2. 插件可以相互依赖，引用依赖插件中的Java代码，根据依赖性安排插件的启动顺序
3. 插件删除时，优化缓存目录
# 不支持：
* 插件中的Android组件，如：活动页面、服务、广播接收者等
1. 有很多插件框架，致力于解决活动页面替换（Activity Plugin）。
2. 我们这里只实现了基础的插件加载管理框架。
3. 可在此基础上通过工厂模式提供视图（View）、片段（Fragment）的替换，达到同样的效果。
* 插件中申明的权限（permission,use-permission）、共享库（usb-library）
# 插件实现：
* 插件就是普通的 APK，但是一般不能直接安装运行
1. 主程序包含的库，插件一般不再包含（减少体积和冲突）
2. 插件不一定有Activity等Android组件，不能启动；但是也可以包含测试用的Activity等
3. 插件可能没有签名，或者只有测试签名
* 插件需要特殊包含的内容：
1. Plugin类：插件加载后初始化入口
2. 插件描述：Xml资源文件，定义插件名称，版本、作者、描述，以及入口类，运行环境，依赖关系等；可以引用其他资源（如@string/title）
3. 插件描述申明：在AndroidManifest中申明
# 应用集成：
* 集成 DroidPlugins.jar
* 集成 libs/armeabi/libidmap_jni.so（可选，仅用于皮肤覆盖）
* 集成自己开发的插件（可选，可运行时按需下载插件，预置放在 assets/plugins/ 目录）
* 应用启动后及时启动插件模块，参考Demo

更多实现细节请参考：https://blog.csdn.net/luansxx/article/details/82965174
