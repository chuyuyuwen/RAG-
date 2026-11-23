package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 整个程序的入口。
 *
 * <p>@SpringBootApplication 是一个组合注解，等于同时开启：
 * <ul>
 *   <li>@Configuration —— 这个类可以定义 Bean</li>
 *   <li>@EnableAutoConfiguration —— Spring Boot 根据 classpath 上的依赖自动配置（看到 web 就配 Tomcat，
 *       看到 h2 就配数据源）</li>
 *   <li>@ComponentScan —— 扫描 com.example.rag 及其子包下所有 @Component / @Service / @Repository /
 *       @RestController，把它们注册成 Bean</li>
 * </ul>
 *
 * <p>所以这个类必须放在所有业务代码的**最外层包**，否则扫描不到。
 */
@SpringBootApplication
public class RagApplication {

    public static void main(String[] args) {
        // 启动 Spring 容器 + 内嵌 Tomcat，阻塞运行直到进程被杀掉
        SpringApplication.run(RagApplication.class, args);
    }
}
