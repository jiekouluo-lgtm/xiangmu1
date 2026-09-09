package cn.edu.rag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "chat-provider", havingValue = "local", matchIfMissing = true)
public class LocalChatProvider implements ChatProvider {
    @Override
    public String name() { return "本地检索摘要（零密钥演示）"; }

    @Override
    public String answer(String question, List<VectorStore.SearchHit> sources) {
        if (sources.isEmpty()) return "知识库中暂时没有找到与问题相关的内容。请先上传资料，或换一种问法。";
        StringBuilder answer = new StringBuilder("根据知识库检索结果，与“")
                .append(question).append("”最相关的内容如下：\n\n");
        int limit = Math.min(3, sources.size());
        for (int i = 0; i < limit; i++) {
            VectorStore.SearchHit source = sources.get(i);
            String text = source.content().replaceAll("\\s+", " ").trim();
            if (text.length() > 260) text = text.substring(0, 260) + "……";
            answer.append(i + 1).append(". ").append(text)
                    .append(" [").append(i + 1).append("]\n\n");
        }
        answer.append("以上内容来自检索片段，建议结合下方原文来源核对。配置大模型接口后，系统会生成更完整的归纳答案。");
        return answer.toString();
    }
}
