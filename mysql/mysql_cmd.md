# mysql命令行基础  

```
GRANT ALL PRIVILEGES ON *.* TO 'test'@'%'
REVOKE ALL ON *.* FROM 'test'@'%'
```

GRANT命令说明：  
- ALL PRIVILEGES 是表示所有权限，你也可以使用select、update等权限
- ON 用来指定权限针对哪些库和表。
- *.* 中前面的*号用来指定数据库名，后面的*号用来指定表名。
- TO 表示将权限赋予某个用户。
