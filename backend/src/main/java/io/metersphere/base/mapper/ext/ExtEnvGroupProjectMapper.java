package io.metersphere.base.mapper.ext;

import io.metersphere.base.domain.EnvironmentGroupProject;

import java.util.List;

public interface ExtEnvGroupProjectMapper {

    List<EnvironmentGroupProject> getList(String groupId);
}
