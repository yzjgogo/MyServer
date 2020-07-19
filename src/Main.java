import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * https://www.cnblogs.com/seve/p/12192245.html
 *
 * http://localhost:8899/test?a=1&b=2
 *
 * Content-Type:浏览器以何种方式处理响应的文本
 * text/html：直接渲染html，以网页结果样式显示；
 * text/plain：以纯文本显示，即使遇到html标签也原样显示；
 *
 * Cache-Control：控制缓存的行为：
 * 经过我的实验，使用Cache-Control max-age=200000000,后chrome浏览器再次请求服务器时，通过浏览器地址栏按enter键无效，仍然是直接访问服务器，F5刷新也是直接访问服务器
 * 只有重新打开浏览器或再打开一个页面标签从地址栏重新输入地址才会使用缓存,具体有没有使用缓存，参考图片：cache-info.png
 *
 * Last-Modified和Etag可以用于配合no-cache，请求服务器验证客户端缓存是否可用
 *
 * 如果服务端响应头有Last-Modified，则下次请求客户端的请求头会带有if-modified-since(值与上次响应的Last-Modified的值相同)，或if-unmodified-since;
 * 用于下次请求时，客户端询问服务端是否可以使用客户端的缓存(缓存是否过期、有变化等)。
 *
 * Etag：服务端可以对响应的内容签名加密等。用于判断客户端的缓存是否可用
 * 如果服务端响应头有Etag，则下次请求客户端的请求头会带有if-none-match(值与上次响应的Etag的值相同)，或if-match;
 * 用于下次请求时，客户端询问服务端是否可以使用客户端的缓存(缓存是否过期、有变化等)。
 * 参考本例,参考图片：cache-certificate.png
 *
 *  建议每次执行操作前，都清理一下浏览器缓存：Chrome缓存清理：设置-->更多工具-->清除浏览数据-->选中项并点击清除数据
 *
 */
public class Main {
    public static void main(String[] arg) throws Exception {
        //创建一个HttpServer实例，并绑定到指定的IP地址和端口号
        HttpServer server = HttpServer.create(new InetSocketAddress(8899), 0);
        //创建一个HttpContext，将路径为/test请求映射到TestHandler处理器
        server.createContext("/test", new TestHandler());
        //设置服务器的线程池对象
//        server.setExecutor(Executors.newFixedThreadPool(10));
        //启动服务器
        server.start();
        System.out.println("server listening 8899");
    }

    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            try {
                StringBuilder responseText = new StringBuilder();
                responseText.append("请求方法：").append(httpExchange.getRequestMethod()).append("<br/>");
                responseText.append("请求URI：").append(httpExchange.getRequestURI().toString()).append("<br/>");
                responseText.append("请求参数：").append(getRequestParam(httpExchange)).append("<br/>");
                responseText.append("请求头：<br/>").append(getRequestHeader(httpExchange));
                Headers headers = httpExchange.getRequestHeaders();
                if (httpExchange.getRequestHeaders().containsKey("If-none-match")) {
                    if (httpExchange.getRequestHeaders().get("If-none-match").contains("777")) {
                        //本次请求的客户端缓存可用
                        handleResponse(httpExchange, "可以使用缓存",true);
                    }else {
                        //本次请求的客户端缓存不可用，返回最新数据
                        handleResponse(httpExchange, responseText.toString(),false);
                    }
                }else {
                    //返回服务器数据
                    handleResponse(httpExchange, responseText.toString(),false);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        /**
         * 获取请求头
         *
         * @param httpExchange
         * @return
         */
        private String getRequestHeader(HttpExchange httpExchange) {
            Headers headers = httpExchange.getRequestHeaders();
            return headers.entrySet().stream()
                    .map((Map.Entry<String, List<String>> entry) -> entry.getKey() + ":" + entry.getValue().toString())
                    .collect(Collectors.joining("<br/>"));
        }

        /**
         * 获取请求参数
         *
         * @param httpExchange
         * @return
         * @throws Exception
         */
        private String getRequestParam(HttpExchange httpExchange) throws Exception {
            String paramStr = "";

            if (httpExchange.getRequestMethod().equals("GET")) {
                //GET请求读queryString
                paramStr = httpExchange.getRequestURI().getQuery();
            } else {
                //非GET请求读请求体
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody(), "utf-8"));
                StringBuilder requestBodyContent = new StringBuilder();
                String line = null;
                while ((line = bufferedReader.readLine()) != null) {
                    requestBodyContent.append(line);
                }
                paramStr = requestBodyContent.toString();
            }

            return paramStr;
        }

        /**
         * 处理响应
         *
         * @param httpExchange
         * @param responsetext
         * @throws Exception
         */
        private void handleResponse(HttpExchange httpExchange, String responsetext,boolean isCanCache) throws Exception {
            //生成html
            StringBuilder responseContent = new StringBuilder();
            responseContent.append("<html>")
                    .append("<body>")
                    .append(responsetext)
                    .append("</body>")
                    .append("</html>");
            String responseContentStr = responseContent.toString();
            byte[] responseContentByte = responseContentStr.getBytes("utf-8");

            //设置响应头，必须在sendResponseHeaders方法之前设置！
//            httpExchange.getResponseHeaders().add("Content-Type", "text/html;charset=utf-8");
            //1：验证Cache-Control，参考图片cache-info.png
//            httpExchange.getResponseHeaders().add("Cache-Control", "max-age=200000000");
            //2：验证no-cache,通过服务器验证后方可判断是否可以使用客户端缓存
            httpExchange.getResponseHeaders().add("Cache-Control", "max-age=200000000,no-cache");
            httpExchange.getResponseHeaders().add("Last-Modified", "123");
            httpExchange.getResponseHeaders().add("Etag", "777");

            //设置响应码和响应体长度，必须在getResponseBody方法之前调用！
            if (isCanCache) {
                //本次请求，客户端缓存可用则，返回响应吗304,告诉客户端使用你的缓存即可，服务端本次就不返回数据了，
                // 如果响应码是304，则Content-length必须是-1,否则报错
                //参考图片：cache-certificate.png
                httpExchange.sendResponseHeaders(304, -1);
                //本次服务端并不返回数据
            }else {
                System.out.println("可以使用缓存200");
                httpExchange.sendResponseHeaders(200, responseContentByte.length);
                OutputStream out = httpExchange.getResponseBody();
                out.write(responseContentByte);
                out.flush();
                out.close();
            }
        }
    }
}
