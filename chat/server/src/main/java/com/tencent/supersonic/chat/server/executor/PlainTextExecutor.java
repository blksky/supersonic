package com.tencent.supersonic.chat.server.executor;

import com.tencent.supersonic.chat.api.pojo.response.QueryResp;
import com.tencent.supersonic.chat.api.pojo.response.QueryResult;
import com.tencent.supersonic.chat.server.agent.Agent;
import com.tencent.supersonic.chat.server.agent.MultiTurnConfig;
import com.tencent.supersonic.chat.server.parser.ParserConfig;
import com.tencent.supersonic.chat.server.pojo.ExecuteContext;
import com.tencent.supersonic.chat.server.service.AgentService;
import com.tencent.supersonic.chat.server.service.ChatManageService;
import com.tencent.supersonic.common.pojo.ChatApp;
import com.tencent.supersonic.common.util.ChatAppManager;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.provider.ModelProvider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.tencent.supersonic.chat.server.parser.ParserConfig.PARSER_MULTI_TURN_ENABLE;

public class PlainTextExecutor implements ChatQueryExecutor {

    private static final String APP_KEY = "SMALL_TALK";
    private static final String INSTRUCTION = "" + "#Role: You are a nice person to talk to.\n"
            + "#Task: Respond quickly and nicely to the user."
            + "#Rules: 1.ALWAYS use the same language as the input.\n" + "#History Inputs: %s\n"
            + "#Current Input: %s\n" + "#Your response: ";

    public PlainTextExecutor() {
        ChatAppManager.register(ChatApp.builder().key(APP_KEY).prompt(INSTRUCTION).name("闲聊对话")
                .description("直接将原始输入透传大模型").enable(true).build());
    }

    @Override
    public QueryResult execute(ExecuteContext executeContext) {
        if (!"PLAIN_TEXT".equals(executeContext.getParseInfo().getQueryMode())) {
            return null;
        }

        AgentService agentService = ContextUtils.getBean(AgentService.class);
        Agent chatAgent = agentService.getAgent(executeContext.getAgent().getId());
        ChatApp chatApp = chatAgent.getChatAppConfig().get(APP_KEY);
        if (!chatApp.isEnable()) {
            return null;
        }

        String promptStr = String.format(chatApp.getPrompt(), getHistoryInputs(executeContext),
                executeContext.getQueryText());
        Prompt prompt = PromptTemplate.from(promptStr).apply(Collections.EMPTY_MAP);
        ChatLanguageModel chatLanguageModel =
                ModelProvider.getChatModel(chatApp.getChatModelConfig());
        Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());

        QueryResult result = new QueryResult();
        result.setQueryState(QueryState.SUCCESS);
        result.setQueryMode(executeContext.getParseInfo().getQueryMode());
        result.setTextResult(response.content().text());

        return result;
    }

    private String getHistoryInputs(ExecuteContext executeContext) {
        StringBuilder historyInput = new StringBuilder();
        List<QueryResp> queryResps = getHistoryQueries(executeContext.getChatId(), 5);
        queryResps.stream().forEach(p -> {
            historyInput.append(p.getQueryText());
            historyInput.append(";");

        });

        return historyInput.toString();
    }

    private List<QueryResp> getHistoryQueries(int chatId, int multiNum) {
        ChatManageService chatManageService = ContextUtils.getBean(ChatManageService.class);
        List<QueryResp> contextualParseInfoList = chatManageService.getChatQueries(chatId).stream()
                .filter(q -> Objects.nonNull(q.getQueryResult())
                        && q.getQueryResult().getQueryState() == QueryState.SUCCESS)
                .collect(Collectors.toList());

        List<QueryResp> contextualList = contextualParseInfoList.subList(0,
                Math.min(multiNum, contextualParseInfoList.size()));
        Collections.reverse(contextualList);

        return contextualList;
    }
}
