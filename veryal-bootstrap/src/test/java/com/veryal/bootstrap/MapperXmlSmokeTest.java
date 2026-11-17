package com.veryal.bootstrap;

import com.veryal.infrastructure.mysql.mapper.ConversationMapper;
import com.veryal.infrastructure.mysql.mapper.FileMapper;
import com.veryal.infrastructure.mysql.mapper.GroupMapper;
import com.veryal.infrastructure.mysql.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper XML 冒烟测试。
 *
 * <p>SQL 从注解搬到 {@code resources/mapper/*.xml} 后，注解阶段能编译的语句在 XML 里
 * 只会在「真正被调用」时才暴露问题（namespace/id 写错、参数名不匹配、标签未闭合、
 * 方言差异）。这里把每个 XML 语句都在 H2 上跑一遍：语法错误会直接抛异常，
 * 绑定错误会抛 {@code Invalid bound statement}。</p>
 *
 * <p>用不存在的 id 查询，所以只验证「能执行 + 返回空结果」，不依赖库里的数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MapperXmlSmokeTest {

    private static final String MISSING_CONVERSATION = "conv-does-not-exist";
    private static final long MISSING_USER = 987654321L;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private FileMapper fileMapper;

    @Test
    void conversationStatementsExecute() {
        assertThat(conversationMapper.selectByUser(MISSING_USER)).isEmpty();
        assertThat(conversationMapper.selectSendContext(MISSING_CONVERSATION, MISSING_USER)).isNull();
        assertThat(conversationMapper.selectCardRows(MISSING_USER)).isEmpty();
        assertThat(conversationMapper.selectMemberIdsPage(MISSING_CONVERSATION, 0L, 10)).isEmpty();
    }

    @Test
    void messageStatementsExecute() {
        assertThat(messageMapper.selectPageAfter(MISSING_CONVERSATION, 0L, 10)).isEmpty();
        assertThat(messageMapper.selectMaxSeq(MISSING_CONVERSATION)).isZero();
        assertThat(messageMapper.searchText(MISSING_CONVERSATION, "%nothing%", 0L,
                Long.MAX_VALUE, 10))
                .isEmpty();
    }

    @Test
    void groupAndFileStatementsExecute() {
        assertThat(groupMapper.selectByUser(MISSING_USER)).isEmpty();
        assertThat(fileMapper.selectTotalBytes()).isGreaterThanOrEqualTo(0L);
    }
}
