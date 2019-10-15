# mybatis 原理简单分析

传统工作模式：
```java
public static void main(String[] args) {
    InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = factory.openSession();
    String name = "tom";
    List<User> list = sqlSession.selectList("com.demo.mapper.UserMapper.getUserByName", name);
}
```
1. 创建`SqlSessionFactoryBuilder`对象，调用`build(inputstream)`方法读取并解析配置文件，返回`SqlSessionFactory`对象;
2. 由`SqlSessionFactory`创建`SqlSession`对象，没有手动设置的话事务默认开启;
3. 调用`SqlSession`中的api，传入Statement Id和参数，内部进行复杂的处理，最后调用jdbc执行SQL语句，封装结果返回。

**开始源码分析**
```java
InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
//这一行代码开始初始化工作
SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream);
```
进入SqlSessionFactoryBuilder类的build方法
```java
// 1.我们最初调用的build
public SqlSessionFactory build(InputStream inputStream) {
	//调用了重载方法
    return build(inputStream, null, null);
  }

// 2.调用的重载方法
public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
    try {
      //  XMLConfigBuilder是专门解析mybatis的配置文件的类，里面涉及将流转换为Document类型，验证等工作
      XMLConfigBuilder parser = new XMLConfigBuilder(inputStream, environment, properties);
      //这里又调用了一个重载方法。parser.parse()的返回值是Configuration对象
      return build(parser.parse());
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error building SqlSession.", e);
    } //省略部分代码
  }
```
看一下parse.parse()的代码
```java
public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //<configuration>是MyBatis配置文件中的顶层标签
    parseConfiguration(parser.evalNode("/configuration"));
    // 返回Configuration实例
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 指定VFS的实现，通过该配置可以加载自定义的虚拟文件系统应用程序
      loadCustomVfs(settings);
      typeAliasesElement(root.evalNode("typeAliases"));
      pluginElement(root.evalNode("plugins"));
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 将settings里的属性设置configuration实例的成员变量上
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      typeHandlerElement(root.evalNode("typeHandlers"));
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
```
Configuration对象的结构和xml配置文件的对象几乎相同。初始化配置文件信息的本质就是创建Configuration对象，将解析的xml数据封装到Configuration内部的属性中。

最后返回一个实现了SqlSessionFactory接口的DefaultSqlSessionFactory实例
```java
public SqlSessionFactory build(Configuration config) {
    return new DefaultSqlSessionFactory(config);
  }
```
接下来执行`SqlSession sqlSession = factory.openSession();`
```java
public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
  }

//ExecutorType 为Executor的类型，TransactionIsolationLevel为事务隔离级别，autoCommit是否开启事务
private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
    Transaction tx = null;
    try {
      // XML的environments设置了运行环境的数据源
      final Environment environment = configuration.getEnvironment();
      // 获取事务管理器，如果没有设置的话会默认创建ManagedTransactionFactory事务管理器
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      // 为数据源创建一个事务
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      // 返回执行器
      final Executor executor = configuration.newExecutor(tx, execType);
      // 返回SqlSession，DefaultSqlSession是SqlSession的实现类
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      closeTransaction(tx); // may have fetched a connection so lets call close()
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }
```
SqlSession是MyBatis中用于和数据库交互的顶层类，通常将它与ThreadLocal绑定，一个会话使用一个SqlSession，并且在使用完毕后需要close。
SqlSession中的两个最重要的参数，configuration与初始化时的相同，Executor为执行器。

执行`sqlSession.selectList`方法
```java
@Override
  public <E> List<E> selectList(String statement) {
    return this.selectList(statement, null);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    try {
      // 根据传入的全限定名+方法名从映射的Map中取出MappedStatement对象
      // 在初始化的时候用mappers标签用来引入mapper.xml文件或者配置mapper接口的目录。
      MappedStatement ms = configuration.getMappedStatement(statement);
      // 调用Executor中的方法处理
      return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }
```
执行query方法
```java
@Override
public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // 获取动态绑定的sql语句
    BoundSql boundSql = ms.getBoundSql(parameter);
    //为本次查询创建缓存的Key
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
}
//进入query的重载方法中
@SuppressWarnings("unchecked")
@Override
public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      queryStack++;
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        // 一级缓存，如果缓存中没有本次查找的值，那么从数据库中查询
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;
    }
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear();
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
}

private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      // 查询的方法
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      localCache.removeObject(key);
    }
    // 设置缓存
    localCache.putObject(key, list);
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }
  
  // SimpleExecutor中实现父类的doQuery抽象方法
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
      Statement stmt = null;
      try {
        Configuration configuration = ms.getConfiguration();
        // 传入参数创建StatementHanlder对象来执行查询
        StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
        // 创建jdbc中的statement对象
        stmt = prepareStatement(handler, ms.getStatementLog());
        // StatementHandler进行处理
        return handler.query(stmt, resultHandler);
      } finally {
        closeStatement(stmt);
      }
    }
  
  // 创建Statement的方法
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
      Statement stmt;
      //条代码中的getConnection方法经过重重调用最后会调用openConnection方法，从连接池中获得连接。
      Connection connection = getConnection(statementLog);
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return stmt;
    }
  //从连接池获得连接的方法
  protected void openConnection() throws SQLException {
      if (log.isDebugEnabled()) {
        log.debug("Opening JDBC Connection");
      }
      //从连接池获得连接
      connection = dataSource.getConnection();
      if (level != null) {
        connection.setTransactionIsolation(level.getLevel());
      }
      setDesiredAutoCommit(autoCommit);
    }
  
  //进入StatementHandler进行处理的query，StatementHandler中默认的是PreparedStatementHandler
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
      PreparedStatement ps = (PreparedStatement) statement;
      //原生jdbc的执行
      ps.execute();
      //处理结果返回。
      return resultSetHandler.handleResultSets(ps);
    }
```

**如果是有接口的话，情况又不太一样**
使用接口查询
```java
public static void main(String[] args) {
    //前三步都相同
    InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream);
    SqlSession sqlSession = factory.openSession();
    //这里不再调用SqlSession 的api，而是获得了接口对象，调用接口中的方法。
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    List<User> list = mapper.getUserByName("tom");
}
```
当判断解析到接口时，会创建此接口对应的MapperProxyFactory对象，存入HashMap中，key = 接口的字节码对象，value = 此接口对应的MapperProxyFactory对象。
```java
public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }
```
进入getMapper方法
```java
//DefaultSqlSession中的getMapper
public <T> T getMapper(Class<T> type) {
    return configuration.<T>getMapper(type, this);
}

//configuration中的给getMapper
public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    return mapperRegistry.getMapper(type, sqlSession);
}

//MapperRegistry中的getMapper
public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
	//从MapperRegistry中的HashMap中拿MapperProxyFactory
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      // 通过动态代理工厂生成示例。
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
}

//MapperProxyFactory类中的newInstance方法
 public T newInstance(SqlSession sqlSession) {
 	// 创建了JDK动态代理的Handler类
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    // 调用了重载方法
    return newInstance(mapperProxy);
  }

//MapperProxy类，实现了InvocationHandler接口
public class MapperProxy<T> implements InvocationHandler, Serializable {
  //省略部分源码	
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;
  private final Map<Method, MapperMethod> methodCache;
  
  // 构造，传入了SqlSession，说明每个session中的代理对象的不同的！
  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }
  //省略部分源码
}

//重载的方法，由动态代理创建新示例返回。
protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
 }
```
在动态代理返回了示例后，我们就可以直接调用mapper类中的方法了，MapperProxy中的invoke方法中已经为我们实现了方法。
```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      //判断调用是是不是Object中定义的方法，toString，hashCode这类非。是的话直接放行。
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    } 
    // 初始化配置文件时被解析封装成一个MappedStatement对象，然后存储在Configuration对象的mappedStatements属性中，mappedStatements 
    // 是一个HashMap，存储时key = 全限定类名 + 方法名，value = 对应的MappedStatement对象。
    // 根据类名和方法名从configuration的mappedStatements取值，先从缓存里取值
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    // 重点在这：MapperMethod最终调用了执行的方法
    return mapperMethod.execute(sqlSession, args);
  }


public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    //判断mapper中的方法类型，最终调用的还是SqlSession中的方法
    switch (command.getType()) {
      case INSERT: {
    	Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional() &&
              (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }
```
以上，mybatis原生工作原理的简单分析结束。
