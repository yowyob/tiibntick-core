package com.yowyob.tiibntick.core.hrm.config;

import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmEmployeeController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmEmployeeSelfServiceController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmExpenseController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmKpiController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmLeaveController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmLoanAdvanceController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmMedicalController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmMedicalSelfServiceController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmMissionOrderController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmRecruitmentController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmReviewController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmReviewSelfServiceController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmSkillController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmSocialDeclarationController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmTimesheetController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmTrainingBudgetController;
import com.yowyob.tiibntick.core.hrm.adapter.in.web.HrmTrainingController;
import com.yowyob.tiibntick.core.hrm.adapter.out.kernel.KernelHrmGatewayAdapter;
import com.yowyob.tiibntick.core.hrm.application.service.KernelHrmGatewayService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the {@code tnt-hrm-core} module — a pure Kernel HRM proxy,
 * no persistence of its own. All beans are plain {@code @Component}/{@code @Service}/
 * {@code @RestController} classes, discovered via this module-local
 * {@link ComponentScan} (redundant with {@code tnt-bootstrap}'s blanket
 * {@code com.yowyob.tiibntick} scan, kept for the module to stay independently
 * bootable/testable — same convention as {@code tnt-actor-core}'s {@code ActorCoreConfig}).
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ComponentScan(basePackageClasses = {
        // ── Kernel outbound adapter + gateway service ───────────────────────────
        KernelHrmGatewayAdapter.class,
        KernelHrmGatewayService.class,
        // ── Web adapters (one controller per Kernel HRM controller) ─────────────
        HrmKpiController.class,
        HrmMedicalSelfServiceController.class,
        HrmMedicalController.class,
        HrmSkillController.class,
        HrmSocialDeclarationController.class,
        HrmEmployeeSelfServiceController.class,
        HrmEmployeeController.class,
        HrmExpenseController.class,
        HrmLeaveController.class,
        HrmLoanAdvanceController.class,
        HrmMissionOrderController.class,
        HrmReviewSelfServiceController.class,
        HrmRecruitmentController.class,
        HrmReviewController.class,
        HrmTimesheetController.class,
        HrmTrainingBudgetController.class,
        HrmTrainingController.class
})
public class TntHrmAutoConfiguration {
}
