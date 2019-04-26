### 准备工作安装redis
可以参考这两位大佬的博文，很详细，因为我用的是阿里云的服务器，所以参考的（1），当然（2）也是可以的，任选一种即可：
（1）《[阿里云 CentOS7安装redis4.0.9并开启远程访问](https://www.cnblogs.com/jepson6669/p/9092634.html "阿里云 CentOS7安装redis4.0.9并开启远程访问")》
（2）《[CentOS7 linux下yum安装redis以及使用](https://www.cnblogs.com/rslai/p/8249812.html "CentOS7 linux下yum安装redis以及使用")》
在这里贴出一组小tips
```dart
进入到bin目录下
查看redis进程
ps -ef | grep redis
停止redis
./redis-cli -h 0.0.0.0 -p 6379 shutdown
启动redis
./redis-server redis.conf
```
## springboot整合redis
##### 1.maven依赖
```dart
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```
##### 2.选择redis客户端
这里选择了lettuce客户端，为什么不用Jredis,在《[spring boot 集成 redis lettuce](http://www.cnblogs.com/taiyonghai/p/9454764.html "spring boot 集成 redis lettuce")》这篇博文里解释了，给大佬点赞。
总结一下就是：
```dart
# Jedis和Lettuce都是Redis Client

# Jedis 是直连模式，在多个线程间共享一个 Jedis 实例时是线程不安全的，
# 如果想要在多线程环境下使用 Jedis，需要使用连接池，
# 每个线程都去拿自己的 Jedis 实例，当连接数量增多时，物理连接成本就较高了。

# Lettuce的连接是基于Netty的，连接实例可以在多个线程间共享，
# 所以，一个多线程的应用可以使用同一个连接实例，而不用担心并发线程的数量。
# 当然这个也是可伸缩的设计，一个连接实例不够的情况也可以按需增加连接实例。

# 通过异步的方式可以让我们更好的利用系统资源，而不用浪费线程等待网络或磁盘I/O。
# Lettuce 是基于 netty 的，netty 是一个多线程、事件驱动的 I/O 框架，
# 所以 Lettuce 可以帮助我们充分利用异步的优势。
```
所以选择maven依赖
```dart
<!-- lettuce pool 缓存连接池 -->
<dependency>
	<groupId>org.apache.commons</groupId>
	<artifactId>commons-pool2</artifactId>
	<version>2.5.0</version>
</dependency>
<!-- jackson json 优化缓存对象序列化 -->
<dependency>
	<groupId>com.fasterxml.jackson.core</groupId>
	<artifactId>jackson-databind</artifactId>
	<version>2.9.6</version>
</dependency>
<!-- https://mvnrepository.com/artifact/com.alibaba/fastjson -->
<dependency>
	<groupId>com.alibaba</groupId>
	<artifactId>fastjson</artifactId>
	<version>1.2.47</version>
</dependency>
```
###### 为什么要添加jackson和fastjson
因为spring-data-redis 里的使用JacksonJsonRedisSerializer序列化（被序列化对象不需要实现Serializable接口，被序列化的结果清晰，容易阅读，而且存储字节少，速度快），我觉得这个json格式不好看，不错，说白了就是格式上不易阅读，还是觉得阿里的fastjson比较适合我的审美观。所以等会要换掉，但是jackson还是要留着，总有不喜欢折腾的同学。
##### 3.配置文件
```d
spring:
#redis
  redis:
    # Redis服务器地址
    host: 你的ip #默认就是localhost
    #host: localhost
    # Redis服务器连接端口
    port: 6379 #默认
    # Redis数据库索引（默认为0）
    database: 0
    # Redis服务器连接密码（默认为空）
    password: 你的redis密码
    # 连接超时时间（毫秒）
    timeout: 10000
    # Lettuce
    lettuce:
      pool:
        # 连接池最大连接数（使用负值表示没有限制）
        max-active: 8
        # 连接池最大阻塞等待时间（使用负值表示没有限制）
        max-wait: 10000
        # 连接池中的最大空闲连接
        max-idle: 8
        # 连接池中的最小空闲连接
        min-idle: 0
      # 关闭超时时间
      shutdown-timeout: 100
```
4.配置类RedisConfig
```d
package warmer.star.blog.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import warmer.star.blog.util.FastJson2JsonRedisSerializer;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 缓存配置-使用Lettuce客户端，自动注入配置的方式
 */
@Configuration
@EnableCaching //启用缓存
@ConfigurationProperties(prefix = "spring.redis") //指明配置节点
public class RedisConfig extends CachingConfigurerSupport {
    // Redis服务器地址
    @Value("${spring.redis.host}")
    private String host;
    // Redis服务器连接端口
    @Value("${spring.redis.port}")
    private Integer port;
    // Redis数据库索引（默认为0）
    @Value("${spring.redis.database}")
    private Integer database;
    // Redis服务器连接密码（默认为空）
    @Value("${spring.redis.password}")
    private String password;
    // 连接超时时间（毫秒）
    @Value("${spring.redis.timeout}")
    private Integer timeout;
    // 连接池最大连接数（使用负值表示没有限制）
    @Value("${spring.redis.lettuce.pool.max-active}")
    private Integer maxTotal;
    // 连接池最大阻塞等待时间（使用负值表示没有限制）
    @Value("${spring.redis.lettuce.pool.max-wait}")
    private Integer maxWait;
    // 连接池中的最大空闲连接
    @Value("${spring.redis.lettuce.pool.max-idle}")
    private Integer maxIdle;
    // 连接池中的最小空闲连接
    @Value("${spring.redis.lettuce.pool.min-idle}")
    private Integer minIdle;
    // 关闭超时时间
    @Value("${spring.redis.lettuce.shutdown-timeout}")
    private Integer shutdown;

    /**
     * 自定义缓存key的生成策略。默认的生成策略是看不懂的(乱码内容) 通过Spring 的依赖注入特性进行自定义的配置注入并且此类是一个配置类可以更多程度的自定义配置
     *
     * @return
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {
            @Override
            public Object generate(Object target, Method method, Object... params) {
                StringBuilder sb = new StringBuilder();
                sb.append(target.getClass().getName());
                sb.append(method.getName());
                for (Object obj : params) {
                    sb.append(obj.toString());
                }
                return sb.toString();
            }
        };
    }

    /**
     * 缓存配置管理器
     */
    @Bean
    @Override
    public CacheManager cacheManager() {
        //以锁写入的方式创建RedisCacheWriter对象
        RedisCacheWriter writer = RedisCacheWriter.lockingRedisCacheWriter(getConnectionFactory());
        /*
        设置CacheManager的Value序列化方式为JdkSerializationRedisSerializer,
        但其实RedisCacheConfiguration默认就是使用
        StringRedisSerializer序列化key，
        JdkSerializationRedisSerializer序列化value,
        所以以下注释代码就是默认实现，没必要写，直接注释掉
         */
        // RedisSerializationContext.SerializationPair pair = RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer(this.getClass().getClassLoader()));
        // RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(pair);
        //创建默认缓存配置对象
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig();
        RedisCacheManager cacheManager = new RedisCacheManager(writer, config);
        return cacheManager;
    }
    /**
     * 获取缓存操作助手对象
     *
     * @return
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        //创建Redis缓存操作助手RedisTemplate对象
        RedisTemplate<String, Object> template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(getConnectionFactory());
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        //RedisTemplate对象需要指明Key序列化方式，如果声明StringRedisTemplate对象则不需要
        template.setKeySerializer(stringRedisSerializer);
        // hash的key也采用String的序列化方式
        template.setHashKeySerializer(stringRedisSerializer);
        //以下代码为将RedisTemplate的Value序列化方式由JdkSerializationRedisSerializer
        // 更换为Jackson2JsonRedisSerializer/FastJson2JsonRedisSerializer两种方式
        //这两种序列化方式结果清晰、容易阅读、存储字节少、速度快，所以推荐更换
        //二者选其一
        /*//1.jackson序列化方式
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(om);
        // value序列化方式采用jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);*/
        //2.FastJson序列化方式
        FastJson2JsonRedisSerializer fastJson2JsonRedisSerializer =new FastJson2JsonRedisSerializer<Object>(Object.class);
        // value序列化方式采用fastson
        template.setValueSerializer(fastJson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(fastJson2JsonRedisSerializer);
        //template.setEnableTransactionSupport(true);//是否启用事务
        template.afterPropertiesSet();
        return template;

    }
    /**
     * 获取缓存连接
     *
     * @return
     */
    @Bean
    public RedisConnectionFactory getConnectionFactory() {
        //单机模式
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(host);
        configuration.setPort(port);
        configuration.setDatabase(database);
        configuration.setPassword(RedisPassword.of(password));
        //哨兵模式
        //RedisSentinelConfiguration configuration1 = new RedisSentinelConfiguration();
        //集群模式
        //RedisClusterConfiguration configuration2 = new RedisClusterConfiguration();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration, getPoolConfig());
        //factory.setShareNativeConnection(false);//是否允许多个线程操作共用同一个缓存连接，默认true，false时每个操作都将开辟新的连接
        return factory;
    }

    /**
     * 获取缓存连接池
     *
     * @return
     */
    @Bean
    public LettucePoolingClientConfiguration getPoolConfig() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(maxTotal);
        config.setMaxWaitMillis(maxWait);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        LettucePoolingClientConfiguration pool = LettucePoolingClientConfiguration.builder()
                .poolConfig(config)
                .commandTimeout(Duration.ofMillis(timeout))
                .shutdownTimeout(Duration.ofMillis(shutdown))
                .build();
        return pool;
    }
}

```
简要说明一下，上文提到的序列化，把jackson换成fastjson,也就是上边redisconfig里127-143行
```java
//以下代码为将RedisTemplate的Value序列化方式由JdkSerializationRedisSerializer
        // 更换为Jackson2JsonRedisSerializer/FastJson2JsonRedisSerializer两种方式
        //这两种序列化方式结果清晰、容易阅读、存储字节少、速度快，所以推荐更换
        //二者选其一
        /*//1.jackson序列化方式
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(om);
        // value序列化方式采用jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);*/
        //2.FastJson序列化方式
        FastJson2JsonRedisSerializer fastJson2JsonRedisSerializer =new FastJson2JsonRedisSerializer<Object>(Object.class);
        // value序列化方式采用fastson
        template.setValueSerializer(fastJson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(fastJson2JsonRedisSerializer);
```
这里对比下，可以切换fastjson和jackson.
###### 5.FastJson2JsonRedisSerializer
使用FastJson2JsonRedisSerializer实现RedisSerializer接口(使用jackson序列化非必须)
```java
package warmer.star.blog.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import java.nio.charset.Charset;
public class FastJson2JsonRedisSerializer <T> implements RedisSerializer<T> {
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
    private Class<T> clazz;
    public FastJson2JsonRedisSerializer(Class<T> clazz) {
        super();
        this.clazz = clazz;
    }
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        return JSON.toJSONString(t, SerializerFeature.WriteClassName).getBytes(DEFAULT_CHARSET);
    }
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        String str = new String(bytes, DEFAULT_CHARSET);
        return (T) JSON.parseObject(str, clazz);
    }
}

```
###### 6.redisUtil工具类
这里参照《[SpringBoot整合Redis及Redis工具类撰写](https://www.cnblogs.com/zeng1994/p/03303c805731afc9aa9c60dbbd32a323.html "SpringBoot整合Redis及Redis工具类撰写")》,搬一下砖,这里强调一下，线上Redis禁止使用Keys正则匹配操作，下边代码注释里也写有，之前又看到过一篇博客《[线上Redis禁止使用Keys正则匹配操作](https://www.jianshu.com/p/2d0e11c551fc "线上Redis禁止使用Keys正则匹配操作")》，里边提到了几种危险的操作，划重点了，大家要记住
```java
package warmer.star.blog.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public final class RedisUtil {
    //若当前class不在spring boot框架内（不在web项目中）所以无法使用autowired，使用此种方法进行注入
    //private static RedisTemplate<String, Object> template = (RedisTemplate<String, Object>) SpringUtils.getBean("redisTemplate");
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    /*
        ###  线上Redis禁止使用Keys正则匹配操作  ###
        1、redis是单线程的，其所有操作都是原子的，不会因并发产生数据异常
        2、使用高耗时的Redis命令是很危险的，会占用唯一的一个线程的大量处理时间，导致所有的请求都被拖慢。（例如时间复杂度为O(N)的KEYS命令，严格禁止在生产环境中使用）
        (1)运维/开发人员进行keys *操作，该操作比较耗时，又因为redis是单线程的，所以redis被锁住。
        (2)此时QPS比较高，又来了几万个对redis的读写请求，因为redis被锁住，所以全部Hang在那。
        (3)因为太多线程Hang在那，CPU严重飙升，造成redis所在的服务器宕机
        (4)所有的线程在redis那取不到数据，一瞬间全去数据库取数据，数据库就宕机了。
        需要注意的是，同样危险的命令不仅有keys *，还有以下几组：
            Flushdb 命令用于清空当前数据库中的所有 key
            Flushall 命令用于清空整个 Redis 服务器的数据(删除所有数据库的所有 key )
            CONFIG 客户端连接后可配置服务器
       参考简书：https://www.jianshu.com/p/2d0e11c551fc
    */

    // =============================common============================
    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒)
     * @return
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 根据key 获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }
    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    @SuppressWarnings("unchecked")
    public void remove(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete(CollectionUtils.arrayToList(key));
            }
        }
    }
    // ============================String=============================
    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }
    /**
     * 普通缓存获取,泛型
     *
     * @param key 键
     * @param clazz 类型
     * @return 值
     */
    public  <T> T get(String key, Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper();
        Object v=get(key);
        if(v==null) return null;
        T val = mapper.convertValue(v,clazz);
        return  val;
    }
    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return
     */
    public long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, delta);
    }
    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return
     */
    public long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, -delta);
    }
    // ================================Map=================================
    /**
     * HashGet
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public Object hget(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }
    /**
     * 获取hashKey对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }
    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, long time) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 删除hash表中的值
     *
     * @param key  键 不能为null
     * @param item 项 可以使多个 不能为null
     */
    public void hdel(String key, Object... item) {
        redisTemplate.opsForHash().delete(key, item);
    }
    /**
     * 判断hash表中是否有该项的值
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }
    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     *
     * @param key  键
     * @param item 项
     * @param by   要增加几(大于0)
     * @return
     */
    public double hincr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, by);
    }
    /**
     * hash递减
     *
     * @param key  键
     * @param item 项
     * @param by   要减少记(小于0)
     * @return
     */
    public double hdecr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, -by);
    }
    // ============================set=============================
    /**
     * 根据key获取Set中的所有值
     *
     * @param key 键
     * @return
     */
    public Set<Object> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */

    public boolean sHasKey(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * 将set数据放入缓存
     *
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */

    public long sSetAndTime(String key, long time, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0)
                expire(key, time);
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * 获取set缓存的长度
     *
     * @param key 键
     * @return
     */
    public long sGetSetSize(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * 移除值为value的
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public long setRemove(String key, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().remove(key, values);
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    // ===============================list=================================
    /**
     * 获取list缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束 0 到 -1代表所有值
     * @return
     */
    public List<Object> lGet(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 获取list缓存的长度
     *
     * @param key 键
     * @return
     */
    public long lGetListSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * 通过索引 获取list中的值
     *
     * @param key   键
     * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return
     */
    public Object lGetIndex(String key, long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, Object value, long time) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0)
                expire(key, time);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, List<Object> value) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, List<Object> value, long time) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0)
                expire(key, time);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 根据索引修改list中的某条数据
     *
     * @param key   键
     * @param index 索引
     * @param value 值
     * @return
     */
    public boolean lUpdateIndex(String key, long index, Object value) {
        try {
            redisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 移除N个值为value
     *
     * @param key   键
     * @param count 移除多少个
     * @param value 值
     * @return 移除的个数
     */

    public long lRemove(String key, long count, Object value) {
        try {
            Long remove = redisTemplate.opsForList().remove(key, count, value);
            return remove;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}

```
### 设计思路：
说在前面，本文先不考虑重复ip等一系列复杂的设计和实现，就是简单的刷新一次就浏览量加1。做法就是在加载页面或者请求接口的时候先看redis是否有该文章的浏览量，有则加1，无则新建为1，然后后台启动同步程序，每隔固定时间往数据中存。
1.常量
```java
/*线上Redis禁止使用Keys正则匹配操作，容易引起缓存雪崩，最终数据库宕机,如
	Set<String> keySet = redisUtil.keys("posts_views::posts_views_id_*");
	所以此处用KEY标识浏览数和点赞数的键，然后存一个集合CODE表示文章ID,点赞数，也就是在外边包一层，里面取集合*/
	/** 浏览数量 的 key**/
	public static final String ARTICLE_VIEWCOUNT_KEY = "article_view";

	/**点赞数量 key**/
	public static final String ARTICLE_APPROVECOUNT_KEY = "article_approve";
	/** 浏览数量每篇 的 key**/
	public static final String ARTICLE_VIEWCOUNT_CODE = "viewcount_";

	/**点赞数每篇的 key**/
	public static final String ARTICLE_APPROVECOUNT_CODE = "approvecount_";
```
2.写入redis操作
```java
//记录浏览量到redis,然后定时更新到数据库
			String key=RedisKey.ARTICLE_VIEWCOUNT_CODE+articleId;
			//找到redis中该篇文章的点赞数，如果不存在则向redis中添加一条
			Map<Object,Object> viewCountItem=redisUtil.hmget(RedisKey.ARTICLE_VIEWCOUNT_KEY);
			Integer viewCount=0;
			if(!viewCountItem.isEmpty()){
				if(viewCountItem.containsKey(key)){
					viewCount=(Integer)viewCountItem.get(key);
					redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,viewCount+1);
				}else {
                    redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,1);
                }
			}else{
				redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,1);
			}
```
3.同步任务
###### maven依赖
```java
<!--quartz相关依赖 -->
<dependency>
	<groupId>org.quartz-scheduler</groupId>
	<artifactId>quartz</artifactId>
</dependency>
<dependency>
	<groupId>org.quartz-scheduler</groupId>
	<artifactId>quartz-jobs</artifactId>
</dependency>
```
###### 启用任务注解
注解模式：在启动类上加@EnableScheduling
```java
@SpringBootApplication
@EnableScheduling
@MapperScan("warmer.star.blog.mapper")
public class BlogApplication extends SpringBootServletInitializer{//war包tomcat模式
	public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BlogApplication.class);  
        application.setBannerMode(Banner.Mode.CONSOLE);
        application.run(args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) 		{
		return application.sources(BlogApplication.class);
	 }
}
```
###### job任务类
```java
package warmer.star.blog.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import warmer.star.blog.enums.RedisKey;
import warmer.star.blog.service.ArticleService;
import warmer.star.blog.util.RedisUtil;

import java.util.Map;

@Component
public class SyncArticleViews {
	protected final Logger logger = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private RedisUtil redisUtil;
	@Autowired
	private ArticleService articleService;
	
	@Scheduled(cron = "0 0/1 * * * ? ")//每1分钟
	public void SyncNodesAndShips() {
		logger.info("开始保存点赞数 、浏览数");
		try {
			//先获取这段时间的浏览数
			Map<Object,Object> viewCountItem=redisUtil.hmget(RedisKey.ARTICLE_VIEWCOUNT_KEY);
			//然后删除redis里这段时间的浏览数
			redisUtil.remove(RedisKey.ARTICLE_VIEWCOUNT_KEY);
			if(!viewCountItem.isEmpty()){
				for(Object item :viewCountItem.keySet()){
					String articleKey=item.toString();//viewcount_1
					String[]  kv=articleKey.split("_");
					Integer articleId=Integer.parseInt(kv[1]);
					Integer viewCount=(Integer) viewCountItem.get(articleKey);
					//更新到数据库
					articleService.updateArticleViewCount(articleId,viewCount);
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		logger.info("结束保存点赞数 、浏览数");
	}
}

```
###### Cron表达式：
在线生成网址：http://cron.qqe2.com/
### GitHub源码：
https://github.com/MiracleTanC/springboot-redis-demo
运行及测试：
程序在IDEA里启动之后直接访问http://localhost:8080/detail/2
后边的数字表示文章id,本文没有关于数据库的读写，若需要自行添加。
![](http://file.miaoleyan.com/nndt/ujRa2vHW6IrFsIWF1ozUgqNvSFXHpI45)
![](http://file.miaoleyan.com/nndt/U25iUBlxkWK3kRQOPQ1Yg7edsJ45ssLs)
# 告辞






