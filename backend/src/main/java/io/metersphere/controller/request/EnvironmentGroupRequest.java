package io.metersphere.controller.request;

import io.metersphere.base.domain.EnvironmentGroup;
import io.metersphere.base.domain.EnvironmentGroupProject;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EnvironmentGroupRequest extends EnvironmentGroup {

    private List<EnvironmentGroupProject> envGroupProject;
}
