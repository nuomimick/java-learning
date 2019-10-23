# gradle常用配置

**配置镜像源**  
a). 配置只在当前项目生效
在 build.gradle 文件内修改或添加 repositories 配置
```
repositories {
    maven {
        url "http://maven.aliyun.com/nexus/content/groups/public"
    }
}
```
b). 配置全局生效
找到 (用户家目录)/.gradle/init.gradle 文件，如果找不到 init.gradle 文件，自己新建一个  
修改/添加 init.gradle 文件内的 repositories 配置
```
allprojects {
    repositories {
        maven {
            url "http://maven.aliyun.com/nexus/content/groups/public"
        }
    }
}
```
