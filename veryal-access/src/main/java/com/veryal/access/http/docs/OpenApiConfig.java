package com.veryal.access.http.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * OpenAPI（Swagger UI）全局配置。
 *
 * <p>接口与字段的说明不再集中维护在这里，而是分散在各个 Controller 与 DTO 上：
 * Controller 用 {@code @Tag} / {@code @Operation}，DTO 用 {@code @Schema}。
 * 本类只负责文档元信息、鉴权方案与标签描述。</p>
 *
 * <p>访问：{@code /swagger-ui.html}</p>
 */
@Configuration
public class OpenApiConfig {

    /** 无需登录即可调用的接口。 */
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/platform/auth/login",
            "POST /api/v1/platform/auth/refresh");

    private static final List<Tag> TAGS = List.of(
            new Tag().name("认证").description("用户注册、登录与当前身份。除注册/登录外均需 Bearer Token。"),
            new Tag().name("用户").description("个人资料、隐私设置、登录设备与操作审计。"),
            new Tag().name("好友").description("好友申请全流程、好友列表与备注。"),
            new Tag().name("黑名单").description("拉黑与解除拉黑，被拉黑方无法发起会话。"),
            new Tag().name("通讯录").description("好友、群与待处理申请的聚合视图。"),
            new Tag().name("会话").description("单聊创建、会话列表、历史消息、发送、已读与撤回。"),
            new Tag().name("消息同步").description("按游标增量拉取，用于上线补拉与断线重连。"),
            new Tag().name("群聊").description("建群、成员与角色、公告、禁言、转让与解散。群成员上限 1000。"),
            new Tag().name("文件").description("普通上传、分片续传、下载地址与删除。"),
            new Tag().name("举报").description("举报用户/群/消息，以及查看自己的举报记录。"),
            new Tag().name("推送").description("设备 token 管理与浏览器 Web Push 订阅。"),
            new Tag().name("运营认证").description("运营后台登录、刷新令牌与双因素认证。账号体系与用户端隔离。"),
            new Tag().name("运营-管理员").description("运营管理员账号的创建、启停、改密与授权。"),
            new Tag().name("运营-用户").description("查询用户、封禁/解封、禁言/解除禁言。"),
            new Tag().name("运营-群组").description("查询群组与强制解散。"),
            new Tag().name("运营-举报").description("举报工单的查询与处理。"),
            new Tag().name("运营-敏感词").description("敏感词库的增删查，命中后拦截消息发送。"),
            new Tag().name("运营-概览").description("平台关键指标看板。"),
            new Tag().name("运营-审计").description("运营操作审计日志。"));

    @Bean
    public OpenAPI veryalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VERYAL IM API")
                        .version("v1.0.0")
                        .description("""
                                VERYAL 即时通讯后端接口。

                                ## 认证方式
                                除「注册」「登录」「运营登录／刷新」外，所有接口都需要在请求头携带：

                                ```
                                Authorization: Bearer <token>
                                ```

                                Token 由 `/api/v1/auth/login` 返回，默认有效期 7 天。
                                点击右上角 **Authorize** 填入 Token 即可在此页面直接调试。

                                ## 两套账号体系
                                - **用户端**：`/api/v1/auth/*`，登录后 `scope=USER`
                                - **运营端**：`/api/v1/platform/auth/*`，登录后 `scope=PLATFORM`

                                两套 Token 互相不能访问对方的接口。

                                ## 统一响应结构
                                ```json
                                { "code": 0, "message": "success", "data": {}, "traceId": null }
                                ```
                                `code=0` 表示成功，非 0 为业务错误码，`message` 可直接展示给用户。

                                ## 分页约定
                                消息类接口使用**游标分页**（`cursor` + `limit`），不使用 OFFSET：
                                传入本地已有的最大序号，返回其后的消息，避免翻页期间因新消息插入而错位。
                                """)
                        .contact(new Contact().name("VERYAL").email("dev@veryal.local"))
                        .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("/").description("当前服务"))
                .tags(TAGS)
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 JWT。在 Authorize 弹窗里直接粘贴，无需手动加 Bearer 前缀。")));
    }

    /**
     * 为需要鉴权的操作挂上安全要求；公开接口不加锁标记。
     */
    @Bean
    public OpenApiCustomizer veryalSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) -> {
                        if (!PUBLIC.contains(method.name() + " " + path)) {
                            operation.addSecurityItem(
                                    new SecurityRequirement().addList("bearerAuth"));
                        }
                    }));
        };
    }
}
