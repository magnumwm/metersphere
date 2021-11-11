package io.metersphere.service;


import io.metersphere.base.domain.EnvironmentGroupProject;
import io.metersphere.base.mapper.ext.ExtEnvGroupProjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author lyh
 */

@Service
@Transactional(rollbackFor = Exception.class)
public class EnvironmentGroupProjectService {

    @Resource
    private ExtEnvGroupProjectMapper extEnvGroupProjectMapper;

    public List<EnvironmentGroupProject> getList(String groupId) {
        return extEnvGroupProjectMapper.getList(groupId);
    }
}
