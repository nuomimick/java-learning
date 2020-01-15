# Ambari笔记

Ambari是hadoop（生态圈）的管理平台，提供hadoop集群部署、管理和监控的服务。通过端口号28888，admin/admin访问管理页面。

#### Ambari扩展第三方框架支持

**Stack，Service， Component的概念**  
Stack：一系列Service组成的软件包，一个Stack可以包含多个版本，例如Stack=HDP-1.3.3  
Service：多个Component（master，slave，client）组成，例如Service=HDFS  
Component：独立的Component有制定的生命周期：start，stop，instal，etc.

**ambari stack定义的文件和结构**
```
metainfo.xml
metrics.json
|_configuration
  my-config-env.xml
|_package
  |_scripts
    master.py
    slave.py
    client.py
    service_check.py
    params.py
```
metainfo.xml 定义什么stack，怎么安装，执行什么脚本
```xml
<?xml version="1.0"?>
<metainfo>
    <schemaVersion>2.0</schemaVersion>
    <services>
        <service>
            <name>DUMMY_APP</name>
            <displayName>My Dummy APP</displayName>
            <comment>This is a distributed app.</comment>
            <version>0.1</version>
            <components>
                <component>
                    <name>DUMMY_MASTER</name>
                    <displayName>Dummy Master Component</displayName>
                    <!--节点类型-->
                    <category>MASTER</category>
                    <!--安装节点数量-->
                    <cardinality>1</cardinality>
                    <!--管理服务的脚本位置，现在脚本只支持python脚本-->
                    <commandScript>
                        <script>scripts/master.py</script>
                        <scriptType>PYTHON</scriptType>
                        <timeout>600</timeout>
                    </commandScript>
                    <customCommands>
                        <customCommand>
                            <name>MYCOMMAND</name>
                            <commandScript>
                                <script>scripts/mycustomcommand.py</script>
                                <scriptType>PYTHON</scriptType>
                                <timeout>600</timeout>
                            </commandScript>
                        </customCommand>
                    </customCommands>
                </component>
                <component>
                    <name>DUMMY_SLAVE</name>
                    <displayName>Dummy Slave Component</displayName>
                    <category>SLAVE</category>
                    <cardinality>1+</cardinality>
                    <commandScript>
                        <script>scripts/slave.py</script>
                        <scriptType>PYTHON</scriptType>
                        <timeout>600</timeout>
                    </commandScript>
                </component>
                <component>
                    <name>DUMMY_CLIENT</name>
                    <displayName>Dummy Client Component</displayName>
                    <category>CLIENT</category>
                    <cardinality>0+</cardinality>
                    <commandScript>
                        <script>scripts/client.py</script>
                        <scriptType>PYTHON</scriptType>
                        <timeout>600</timeout>
                    </commandScript>
                </component>
            </components>
            <!--该标签描述这个组件需要的依赖。Ambari 会使用yum或者apt-get去安装这些依赖-->
            <osSpecifics>
                <osSpecific>
                    <!--支持 Linux 的发行版本-->
                    <osFamily>any</osFamily>
                    <!--依赖的包-->
                    <packages>
                        <package>
                            <name>imagemagick</name>
                        </package>
                        <package>
                            <name>dummy-app</name>
                        </package>
                    </packages>
                </osSpecific>
            </osSpecifics>
            <commandScript>
                <script>scripts/service_check.py</script>
                <scriptType>PYTHON</scriptType>
                <timeout>300</timeout>
            </commandScript>
            <requiredServices>
                <service>HDFS</service>
                <service>YARN</service>
            </requiredServices>
            <configuration-dependencies>
                <!--该文件里的属性配置会显示在页面上，添加一个新的配置文件记得先删除service，再添加service，才能在页面上显示-->
                <config-type>my-config-env</config-type>
            </configuration-dependencies>
        </service>
    </services>
</metainfo>
```
service标签里是你安装的服务，包含components, osSpecifics, commandScript, requiredServices 和configuration-dependencies。  
components标签定义你要分发应用的拓扑结构。当前示例我们有MASTER节点，SLAVE节点和CLIENT节点。Ambari期望知道如何管理分布应用的拓扑结构。    

commandScript脚本master.py示例：
```python
import sys
from resource_management import *
class DummyMaster(Script):
    def install(self, env):
        import params
        env.set_params(params)
        print 'Install the Master'
        self.install_packages(env)
    def stop(self, env):
        print 'Stop the Master'
    def start(self, env):
        import params
        env.set_params(params)
        print 'Start the Master'
    def status(self, env):
        print 'Status of the Master'
if __name__ == "__main__":
    DummyMaster().execute()
```

`scripts/service_check.py`检测组件的状态，一般会在脚本里做些组件的相关操作。例如Kafka里添加topic、查询topic数量等操作。

`scripts/param.py`读取configuration文件夹（不止这个文件夹，有些配置不知道从哪里来的）中.xml文件定义的各个配置变量，有需要的话进行进一步加工。
```
config = Script.get_config()
// 读取configuration文件夹下的file1.xml文件里的key1属性值
config['configurations']['file1']['key1']
// 以上等同于下面语句，如果没有这个属性值就设置为第二个参数
default("/configurations/kafka-broker/zookeeper.connect", None)
```

**参数模板配置**  
可以在templates目录下创建模板文件，如下所示：
```
{{abc}}-{{abc}}
{{abc}}-{{abc}}
```
{{abc}}的变量表示引用param.py里的变量  
然后我们可以用代码创建一个文件，如下所示：
```
File(os.path.join(params.conf_dir, 'test.conf'),
     owner='root',
     group='root',
     mode=0644,
     content=Template("test.j2")
)
# 或者
File(format("{conf_dir}/test.conf"),
     owner=params.abc,
     content=InlineTemplate(params.test)
)
```
