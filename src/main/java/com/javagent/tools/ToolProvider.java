package com.javagent.tools;

import java.util.List;

/**
 * 工具插件接口 —— SPI 扩展点
 *
 * 实现此接口并在 META-INF/services/com.javagent.tools.ToolProvider 中注册，
 * 即可自动加载自定义工具，无需修改核心代码。
 *
 * 示例：
 *   public class MyToolProvider implements ToolProvider {
 *       public List<Tool> tools() {
 *           return List.of(new MyCustomTool());
 *       }
 *   }
 *
 * 然后在 META-INF/services/com.javagent.tools.ToolProvider 中写入：
 *   com.example.MyToolProvider
 */
public interface ToolProvider {
    /** 返回此插件提供的所有工具 */
    List<Tool> tools();
}
