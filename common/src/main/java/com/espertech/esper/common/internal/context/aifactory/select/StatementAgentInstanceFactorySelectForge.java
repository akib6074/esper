/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.common.internal.context.aifactory.select;

import com.espertech.esper.common.internal.bytecodemodel.base.CodegenClassScope;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethod;
import com.espertech.esper.common.internal.bytecodemodel.base.CodegenMethodScope;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpression;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder;
import com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionNewAnonymousClass;
import com.espertech.esper.common.internal.context.activator.ViewableActivator;
import com.espertech.esper.common.internal.context.activator.ViewableActivatorForge;
import com.espertech.esper.common.internal.context.aifactory.core.SAIFFInitializeSymbol;
import com.espertech.esper.common.internal.context.aifactory.core.StatementAgentInstanceFactoryForge;
import com.espertech.esper.common.internal.epl.expression.core.ExprForge;
import com.espertech.esper.common.internal.epl.expression.core.ExprNodeUtilityPrint;
import com.espertech.esper.common.internal.epl.expression.subquery.ExprSubselectNode;
import com.espertech.esper.common.internal.epl.expression.table.ExprTableAccessNode;
import com.espertech.esper.common.internal.epl.join.base.JoinSetComposerPrototypeForge;
import com.espertech.esper.common.internal.epl.output.core.OutputProcessViewDirectSimpleFactoryProvider;
import com.espertech.esper.common.internal.epl.subselect.SubSelectFactoryForge;
import com.espertech.esper.common.internal.epl.table.strategy.ExprTableEvalStrategyFactoryForge;
import com.espertech.esper.common.internal.epl.table.strategy.ExprTableEvalStrategyUtil;
import com.espertech.esper.common.internal.view.access.ViewResourceDelegateDesc;
import com.espertech.esper.common.internal.view.core.ViewFactory;
import com.espertech.esper.common.internal.view.core.ViewFactoryForge;
import com.espertech.esper.common.internal.view.core.ViewFactoryForgeUtil;

import java.util.List;
import java.util.Map;

import static com.espertech.esper.common.internal.bytecodemodel.model.expression.CodegenExpressionBuilder.*;
import static com.espertech.esper.common.internal.epl.expression.core.ExprNodeUtilityCodegen.codegenEvaluator;

public class StatementAgentInstanceFactorySelectForge implements StatementAgentInstanceFactoryForge {
    private final static String RSPFACTORYPROVIDER = "rspFactoryProvider";

    private final String[] streamNames;
    private final ViewableActivatorForge[] viewableActivatorForges;
    private final String resultSetProcessorProviderClassName;
    private final List<ViewFactoryForge>[] views;
    private final ViewResourceDelegateDesc[] viewResourceDelegates;
    private final ExprForge whereClauseForge;
    private final JoinSetComposerPrototypeForge joinSetComposerPrototypeForge;
    private final String outputProcessViewProviderClassName;
    private final boolean outputProcessViewDirectSimple;
    private final Map<ExprSubselectNode, SubSelectFactoryForge> subselects;
    private final Map<ExprTableAccessNode, ExprTableEvalStrategyFactoryForge> tableAccesses;
    private final boolean orderByWithoutOutputRateLimit;
    private final boolean unidirectionalJoin;

    public StatementAgentInstanceFactorySelectForge(String[] streamNames, ViewableActivatorForge[] viewableActivatorForges, String resultSetProcessorProviderClassName, List<ViewFactoryForge>[] views, ViewResourceDelegateDesc[] viewResourceDelegates, ExprForge whereClauseForge, JoinSetComposerPrototypeForge joinSetComposerPrototypeForge, String outputProcessViewProviderClassName, boolean outputProcessViewDirectSimple, Map<ExprSubselectNode, SubSelectFactoryForge> subselects, Map<ExprTableAccessNode, ExprTableEvalStrategyFactoryForge> tableAccesses, boolean orderByWithoutOutputRateLimit, boolean unidirectionalJoin) {
        this.streamNames = streamNames;
        this.viewableActivatorForges = viewableActivatorForges;
        this.resultSetProcessorProviderClassName = resultSetProcessorProviderClassName;
        this.views = views;
        this.viewResourceDelegates = viewResourceDelegates;
        this.whereClauseForge = whereClauseForge;
        this.joinSetComposerPrototypeForge = joinSetComposerPrototypeForge;
        this.outputProcessViewProviderClassName = outputProcessViewProviderClassName;
        this.outputProcessViewDirectSimple = outputProcessViewDirectSimple;
        this.subselects = subselects;
        this.tableAccesses = tableAccesses;
        this.orderByWithoutOutputRateLimit = orderByWithoutOutputRateLimit;
        this.unidirectionalJoin = unidirectionalJoin;
    }

    public CodegenMethod initializeCodegen(CodegenClassScope classScope, CodegenMethodScope parent, SAIFFInitializeSymbol symbols) {
        CodegenMethod method = parent.makeChild(StatementAgentInstanceFactorySelect.EPTYPE, this.getClass(), classScope);
        method.getBlock()
                .declareVarNewInstance(StatementAgentInstanceFactorySelect.EPTYPE, "saiff");

        // stream names
        method.getBlock().exprDotMethod(ref("saiff"), "setStreamNames", constant(streamNames));

        // activators
        method.getBlock().declareVar(ViewableActivator.EPTYPEARRAY, "activators", newArrayByLength(ViewableActivator.EPTYPE, constant(viewableActivatorForges.length)));
        for (int i = 0; i < viewableActivatorForges.length; i++) {
            method.getBlock().assignArrayElement("activators", constant(i), viewableActivatorForges[i].makeCodegen(method, symbols, classScope));
        }
        method.getBlock().exprDotMethod(ref("saiff"), "setViewableActivators", ref("activators"));

        // views
        if (views.length == 1 && views[0].isEmpty()) {
            method.getBlock().exprDotMethod(ref("saiff"), "setViewFactories", publicConstValue(ViewFactory.EPTYPE, "SINGLE_ELEMENT_ARRAY"));
        } else {
            method.getBlock().declareVar(ViewFactory.EPTYPEARRAYARRAY, "viewFactories", newArrayByLength(ViewFactory.EPTYPEARRAY, constant(views.length)));
            for (int i = 0; i < views.length; i++) {
                if (views[i] != null) {
                    CodegenExpression array = ViewFactoryForgeUtil.codegenForgesWInit(views[i], i, null, method, symbols, classScope);
                    method.getBlock().assignArrayElement("viewFactories", constant(i), array);
                }
            }
            method.getBlock().exprDotMethod(ref("saiff"), "setViewFactories", ref("viewFactories"));
        }

        // view delegate information ('prior' and 'prev')
        method.getBlock().exprDotMethod(ref("saiff"), "setViewResourceDelegates", ViewResourceDelegateDesc.toExpression(viewResourceDelegates));

        // result set processor
        method.getBlock().declareVar(resultSetProcessorProviderClassName, RSPFACTORYPROVIDER, CodegenExpressionBuilder.newInstance(resultSetProcessorProviderClassName, symbols.getAddInitSvc(method)))
                .exprDotMethod(ref("saiff"), "setResultSetProcessorFactoryProvider", ref(RSPFACTORYPROVIDER));

        // where-clause evaluator
        if (whereClauseForge != null) {
            CodegenExpressionNewAnonymousClass whereEval = codegenEvaluator(whereClauseForge, method, this.getClass(), classScope);
            method.getBlock().exprDotMethod(ref("saiff"), "setWhereClauseEvaluator", whereEval);
            if (classScope.isInstrumented()) {
                method.getBlock().exprDotMethod(ref("saiff"), "setWhereClauseEvaluatorTextForAudit", constant(ExprNodeUtilityPrint.toExpressionStringMinPrecedence(whereClauseForge)));
            }
        }

        // joins
        if (joinSetComposerPrototypeForge != null) {
            method.getBlock().exprDotMethod(ref("saiff"), "setJoinSetComposerPrototype", joinSetComposerPrototypeForge.make(method, symbols, classScope));
        }

        // output process view
        CodegenExpression opv;
        if (outputProcessViewDirectSimple) {
            opv = publicConstValue(OutputProcessViewDirectSimpleFactoryProvider.EPTYPE, "INSTANCE");
        } else {
            opv = CodegenExpressionBuilder.newInstance(outputProcessViewProviderClassName, symbols.getAddInitSvc(method));
        }
        method.getBlock().exprDotMethod(ref("saiff"), "setOutputProcessViewFactoryProvider", opv);

        // subselects
        if (!subselects.isEmpty()) {
            method.getBlock().exprDotMethod(ref("saiff"), "setSubselects", SubSelectFactoryForge.codegenInitMap(subselects, this.getClass(), method, symbols, classScope));
        }

        // table-access
        if (!tableAccesses.isEmpty()) {
            method.getBlock().exprDotMethod(ref("saiff"), "setTableAccesses", ExprTableEvalStrategyUtil.codegenInitMap(tableAccesses, this.getClass(), method, symbols, classScope));
        }

        // order-by with no output-limit
        if (orderByWithoutOutputRateLimit) {
            method.getBlock().exprDotMethod(ref("saiff"), "setOrderByWithoutOutputRateLimit", constant(orderByWithoutOutputRateLimit));
        }

        // unidirectional join
        if (unidirectionalJoin) {
            method.getBlock().exprDotMethod(ref("saiff"), "setUnidirectionalJoin", constant(unidirectionalJoin));
        }

        method.getBlock().methodReturn(ref("saiff"));

        return method;
    }
}
