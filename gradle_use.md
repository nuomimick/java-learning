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


**自动打成Jar包**  
build.gradle配置如下：
```
group 'my.demo'
version '1.0-SNAPSHOT'

apply plugin: 'java'

apply plugin: 'idea'

sourceCompatibility = 1.8

dependencies {
    testCompile group: 'junit', name: 'junit', version: '4.12'
    compile group: 'org.apache.kafka', name: 'kafka-clients', version: '2.3.0'
}

jar {
    // 以下两个属性会覆盖上面配置的version字段
    baseName = 'kafka-demo'
    version = '1.0'
    manifest {
        attributes 'Main-Class': 'kafka.Demo01'
    }
    from {
        configurations.compile.collect { it.isDirectory() ? it : zipTree(it) }
    }
}

task copyJar(type: Copy) {
    // configurations.runtime 就是运行时环境，也就是依赖包
    from configurations.runtime
    into('build/libs/lib')
}

//把JAR发布到目标目录
task release(type: Copy, dependsOn: [build, copyJar]) {
//    from  'conf'
//    into ('build/libs/eachend/conf')
}

```
