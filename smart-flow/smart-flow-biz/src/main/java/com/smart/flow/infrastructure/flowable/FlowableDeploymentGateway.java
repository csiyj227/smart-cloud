package com.smart.flow.infrastructure.flowable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

/**
 * Thin façade in front of {@link RepositoryService} for deploying / suspending / activating
 * BPMN models.
 *
 * <p>This indirection serves two purposes:
 * <ol>
 *   <li>application services depend on a Smart-owned interface instead of leaking the
 *       Flowable API across layers - if we ever swap the engine the blast radius shrinks
 *       dramatically;</li>
 *   <li>it is the single place where Flowable-specific exceptions get translated into the
 *       Smart exception hierarchy, keeping the rest of the codebase quiet.</li>
 * </ol>
 *
 * <p>The class deliberately holds no state; it is safe to share across threads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowableDeploymentGateway {

    private final RepositoryService repositoryService;

    /**
     * Deploys the given BPMN XML under a deployment name derived from the chart key + version.
     * Returns the freshly-issued deployment id and process-definition id, both of which the
     * caller must persist on the {@code flow_definition} row.
     */
    public DeployResult deploy(String chartKey, Integer chartVersion, String bpmnXml) {
        String deploymentName = chartKey + ":v" + chartVersion;
        // The resource name must end in ".bpmn" or ".bpmn20.xml" for Flowable to parse it as BPMN.
        String resourceName = chartKey + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(deploymentName)
                .key(chartKey)
                .addString(resourceName, bpmnXml)
                .deploy();

        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        if (processDefinition == null) {
            // Flowable should never accept a deployment without producing a definition; this
            // branch exists purely to surface a clearer error if the contract is ever broken.
            throw new IllegalStateException(
                    "Flowable accepted deployment " + deployment.getId()
                            + " but produced no ProcessDefinition - inspect act_re_procdef directly");
        }
        log.info("Deployed chart {} v{} -> deploymentId={}, processDefinitionId={}",
                chartKey, chartVersion, deployment.getId(), processDefinition.getId());
        return new DeployResult(deployment.getId(), processDefinition.getId());
    }

    /** Removes a deployment and all its data (cascade). Used when archiving an old version. */
    public void undeploy(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return;
        }
        repositoryService.deleteDeployment(deploymentId, true);
        log.info("Undeployed Flowable deployment {}", deploymentId);
    }

    /** Result of a successful deployment - lightweight value object. */
    public record DeployResult(String deploymentId, String processDefinitionId) {
    }
}
