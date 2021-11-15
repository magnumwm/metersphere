package io.metersphere.service;

import io.metersphere.base.domain.EnvironmentGroup;
import io.metersphere.base.domain.EnvironmentGroupExample;
import io.metersphere.base.domain.EnvironmentGroupProject;
import io.metersphere.base.domain.EnvironmentGroupProjectExample;
import io.metersphere.base.mapper.EnvironmentGroupMapper;
import io.metersphere.base.mapper.EnvironmentGroupProjectMapper;
import io.metersphere.base.mapper.ext.ExtEnvironmentGroupMapper;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.controller.request.EnvironmentGroupRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * @author lyh
 */

@Service
@Transactional(rollbackFor = Exception.class)
public class EnvironmentGroupService {

    @Resource
    private EnvironmentGroupMapper environmentGroupMapper;
    @Resource
    private ExtEnvironmentGroupMapper extEnvironmentGroupMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;
    @Resource
    private EnvironmentGroupProjectMapper environmentGroupProjectMapper;

    public EnvironmentGroup add(EnvironmentGroupRequest request) {
        request.setWorkspaceId(SessionUtils.getCurrentWorkspaceId());
        this.checkEnvironmentGroup(request);

        request.setId(UUID.randomUUID().toString());
        request.setCreateTime(System.currentTimeMillis());
        request.setUpdateTime(System.currentTimeMillis());
        environmentGroupMapper.insertSelective(request);
        this.insertGroupProject(request);

        return request;
    }

    private void insertGroupProject(EnvironmentGroupRequest request) {
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        EnvironmentGroupProjectMapper mapper = sqlSession.getMapper(EnvironmentGroupProjectMapper.class);

        List<EnvironmentGroupProject> envGroupProject = request.getEnvGroupProject();
        // todo 项目重复校验
        for (EnvironmentGroupProject egp : envGroupProject) {
            String projectId = egp.getProjectId();
            String environmentId = egp.getEnvironmentId();
            if (StringUtils.isBlank(projectId) || StringUtils.isBlank(environmentId)) {
                continue;
            }
            EnvironmentGroupProject e = new EnvironmentGroupProject();
            // todo 检查 项目｜环境 是否存在
            e.setId(UUID.randomUUID().toString());
            e.setDescription(egp.getDescription());
            e.setEnvironmentGroupId(request.getId());
            e.setProjectId(projectId);
            e.setEnvironmentId(environmentId);
            mapper.insert(e);
        }
        sqlSession.flushStatements();
    }

    public List<EnvironmentGroup> getList(EnvironmentGroupRequest request) {
        return extEnvironmentGroupMapper.getList(request);
    }

    public void delete(String id) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        environmentGroupMapper.deleteByPrimaryKey(id);
        EnvironmentGroupProjectExample example = new EnvironmentGroupProjectExample();
        example.createCriteria().andEnvironmentGroupIdEqualTo(id);
        environmentGroupProjectMapper.deleteByExample(example);
    }

    public EnvironmentGroup update(EnvironmentGroupRequest request) {
        request.setWorkspaceId(SessionUtils.getCurrentWorkspaceId());
        if (StringUtils.isNotBlank(request.getName())) {
            this.checkEnvironmentGroup(request);
        }
        request.setUpdateTime(System.currentTimeMillis());
        environmentGroupMapper.updateByPrimaryKeySelective(request);

        String envGroupId = request.getId();
        EnvironmentGroupProjectExample example = new EnvironmentGroupProjectExample();
        example.createCriteria().andEnvironmentGroupIdEqualTo(envGroupId);
        environmentGroupProjectMapper.deleteByExample(example);

        this.insertGroupProject(request);

        return request;
    }

    private void checkEnvironmentGroup(EnvironmentGroupRequest request) {
        String name = request.getName();
        if (StringUtils.isBlank(name)) {
            MSException.throwException("environment group name is null.");
        }

        EnvironmentGroupExample environmentGroupExample = new EnvironmentGroupExample();
        EnvironmentGroupExample.Criteria criteria = environmentGroupExample.createCriteria();
        criteria.andNameEqualTo(name)
                .andWorkspaceIdEqualTo(request.getWorkspaceId());
        if (StringUtils.isNotBlank(request.getId())) {
            criteria.andIdNotEqualTo(request.getId());
        }

        if (environmentGroupMapper.countByExample(environmentGroupExample) > 0) {
            MSException.throwException("环境组已存在！");
        }
    }

    public List<EnvironmentGroup> getRelateProjectGroup(String projectId) {
        return extEnvironmentGroupMapper.getRelateProject(SessionUtils.getCurrentWorkspaceId(), projectId);
    }
}
