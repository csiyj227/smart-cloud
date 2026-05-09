package com.smart.flow.infrastructure.assignee;

import com.smart.flow.api.assignee.AssigneeStrategyType;
import com.smart.flow.domain.assignee.AssigneeContext;
import com.smart.flow.domain.assignee.AssigneeResolution;
import com.smart.flow.domain.assignee.AssigneeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Evaluates a SpEL expression against the runtime context to compute assignees.
 *
 * <p>Parameters consumed:
 * <ul>
 *   <li>{@code expression} - SpEL expression string, required. The expression is evaluated
 *       against an {@link StandardEvaluationContext} pre-populated with three root variables:
 *       <ul>
 *         <li>{@code #starter} - the starter user id ({@link Long});</li>
 *         <li>{@code #previousActors} - the previous-actor list ({@code List&lt;Long&gt;});</li>
 *         <li>{@code #form} - the form variables map.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>The expression must evaluate to either a single {@link Number} or a {@code List&lt;Number&gt;};
 * anything else is treated as a configuration error.
 *
 * <p>Compiled expressions are cached in-memory keyed by expression text. The cache is
 * unbounded but bounded in practice by the number of distinct SpEL expressions across all
 * deployed flow definitions, which is typically tiny (a handful per tenant); we therefore opt
 * for a {@link ConcurrentHashMap} rather than dragging in a full LRU implementation.
 */
@Slf4j
@Component
public class SpelAssigneeResolver implements AssigneeResolver {

    private static final String PARAM_EXPRESSION = "expression";

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Override
    public String strategyKey() {
        return AssigneeStrategyType.SPEL.key();
    }

    @Override
    public AssigneeResolution resolve(AssigneeContext context) {
        Object raw = context.parametersSafe().get(PARAM_EXPRESSION);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
                    "SpEL strategy on node '" + context.getNodeKey()
                            + "' requires a non-blank 'expression' parameter");
        }

        Expression expression = compileOrFail(text, context.getNodeKey());
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("starter", context.getStarterUserId());
        ctx.setVariable("previousActors", context.previousActorsSafe());
        ctx.setVariable("form", context.formVariablesSafe());

        Object value;
        try {
            value = expression.getValue(ctx);
        } catch (EvaluationException e) {
            throw new IllegalArgumentException(
                    "SpEL strategy on node '" + context.getNodeKey()
                            + "' failed to evaluate '" + text + "': " + e.getMessage(), e);
        }
        return AssigneeResolution.of(coerce(value, context.getNodeKey()), strategyKey());
    }

    private Expression compileOrFail(String text, String nodeKey) {
        return expressionCache.computeIfAbsent(text, key -> {
            try {
                return parser.parseExpression(key);
            } catch (ParseException e) {
                throw new IllegalArgumentException(
                        "SpEL strategy on node '" + nodeKey
                                + "' could not parse expression '" + key + "': " + e.getMessage(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Long> coerce(Object value, String nodeKey) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Number n) {
            return List.of(n.longValue());
        }
        if (value instanceof List<?> list) {
            List<Long> result = new ArrayList<>(list.size());
            for (Object element : list) {
                if (!(element instanceof Number n)) {
                    throw new IllegalArgumentException(
                            "SpEL strategy on node '" + nodeKey
                                    + "' returned a list element that is not a number: " + element);
                }
                result.add(n.longValue());
            }
            return result;
        }
        throw new IllegalArgumentException(
                "SpEL strategy on node '" + nodeKey
                        + "' must evaluate to a Number or List<Number>, got " + value.getClass().getName());
    }
}
