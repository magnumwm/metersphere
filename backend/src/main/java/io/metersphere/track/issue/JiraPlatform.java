package io.metersphere.track.issue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.IssuesDao;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.base.domain.Project;
import io.metersphere.base.mapper.IssuesMapper;
import io.metersphere.base.mapper.ext.ExtFileContentMapper;
import io.metersphere.commons.constants.CustomFieldType;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.commons.constants.IssuesStatus;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.*;
import io.metersphere.dto.*;
import io.metersphere.service.CustomFieldService;
import io.metersphere.service.SystemParameterService;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.issue.client.JiraAbstractClient;
import io.metersphere.track.issue.client.JiraClientV2;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.issue.domain.ProjectIssueConfig;
import io.metersphere.track.issue.domain.jira.*;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import io.metersphere.track.service.IssuesService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.fastjson.JSON.parseArray;

public class JiraPlatform extends AbstractIssuePlatform {

    protected JiraClientV2 jiraClientV2;

    public JiraPlatform(IssuesRequest issuesRequest) {
        super(issuesRequest);
        this.key = IssuesManagePlatform.Jira.name();
        jiraClientV2 = new JiraClientV2();
        setConfig();
    }

    @Override
    public List<IssuesDao> getIssue(IssuesRequest issuesRequest) {
        issuesRequest.setPlatform(key);
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issuesRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issuesRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issuesRequest);
        }
        return issues;
    }

    public IssuesWithBLOBs getUpdateIssue(IssuesWithBLOBs issue, JiraIssue jiraIssue) {
        if (issue == null) {
            issue = new IssuesWithBLOBs();
            issue.setCustomFields(defaultCustomFields);
        } else {
            mergeCustomField(issue, defaultCustomFields);
        }

        JSONObject renderedFields = jiraIssue.getRenderedFields();
        JSONObject fields = jiraIssue.getFields();
        String status = getStatus(fields);
        String desc = renderedFields.getString("description");
        String description = jiraClientV2.jiraHtmlDesc2MsDesc(desc);
        fields.put("description", description);
        jiraIssue.setFields(fields);
        Parser parser = Parser.builder().build();
        // 转成通用html格式
        if (StringUtils.isNotBlank(description)) {
            Node document = parser.parse(description);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            description = renderer.render(document);
        }

        JSONObject assignee = (JSONObject) fields.get("assignee");
        JSONObject reporter = (JSONObject) fields.get("reporter");
        issue.setTitle(fields.getString("summary"));
        issue.setCreateTime(fields.getLong("created"));
        issue.setUpdateTime(fields.getLong("updated"));
        issue.setLastmodify(assignee == null ? "" : assignee.getString("displayName"));
        issue.setReporter(reporter == null ? "" : reporter.getString("displayName"));
        issue.setDescription(description);
        issue.setPlatformStatus(status);
        issue.setPlatform(key);
        issue.setNum(getNextNum(issue.getProjectId()));
        issue.setCustomFields(syncIssueCustomField(issue.getCustomFields(), jiraIssue.getFields()));
        return issue;
    }

    private String getStatus(JSONObject fields) {
        JSONObject statusObj = (JSONObject) fields.get("status");
        if (statusObj != null) {
            JSONObject statusCategory = (JSONObject) statusObj.get("statusCategory");
            return statusCategory.getString("name");
        }
        return "";
    }

    private List<IssueAttachment> getAttachments(JSONObject fields) {
        JSONArray attachmentArr = fields.getJSONArray("attachment");
        List<IssueAttachment> IssueAttachmentList = new ArrayList<>();
        if (attachmentArr != null) {
            for (int i=0; i< attachmentArr.size(); i++) {
                JSONObject item = attachmentArr.getJSONObject(i);
                IssueAttachment issueAttachment = new IssueAttachment();
                issueAttachment.setFilename(item.getString("filename"));
                issueAttachment.setContent(item.getString("content"));
                issueAttachment.setThumbnail(item.getString("thumbnail"));
                IssueAttachmentList.add(issueAttachment);
            }
        }
        return IssueAttachmentList;
    }

    @Override
    public List<DemandDTO> getDemandList(String projectId) {
        List<DemandDTO> list = new ArrayList<>();
        Project project = getProject();
        int maxResults = 50, startAt = 0;
        JSONArray demands;
        do {
            demands = jiraClientV2.getDemands(project.getJiraKey(), getStoryType(project.getIssueConfig()), startAt, maxResults);
            for (int i = 0; i < demands.size(); i++) {
                JSONObject o = demands.getJSONObject(i);
                String issueKey = o.getString("key");
                JSONObject fields = o.getJSONObject("fields");
                String summary = fields.getString("summary");
                DemandDTO demandDTO = new DemandDTO();
                demandDTO.setName(summary);
                demandDTO.setId(issueKey);
                demandDTO.setPlatform(key);
                list.add(demandDTO);
            }
            startAt += maxResults;
        } while (demands.size() >= maxResults);
        return list;
    }

    @Override
    public List<IssuesDao> getAllIssuesList(String projectId) {
        List<IssuesDao> jiraList = new ArrayList<>();
        Project project = getProject();
        int maxResults = 50, startAt = 0;
        JSONArray issues;
        do {
            issues = jiraClientV2.getProjectIssues(startAt, maxResults, project.getJiraKey(), getIssueType(project.getIssueConfig()));
            for (int i = 0; i < issues.size(); i++) {
                IssuesDao issuesDao = new IssuesDao();
                JSONObject issue = issues.getJSONObject(i);
                String issueKey = issue.getString("key");
                JSONObject fields = issue.getJSONObject("fields");
                String jira_status = fields.getJSONObject("status").getString("name");
                String summary = fields.getString("summary");
                issuesDao.setTitle(summary);
                issuesDao.setCreateTime(fields.getLong("created"));
                issuesDao.setUpdateTime(fields.getLong("updated"));
                issuesDao.setId(UUID.randomUUID().toString());
                issuesDao.setPlatform(project.getPlatform());
                issuesDao.setPlatformId(issueKey);
                issuesDao.setPlatformStatus(jira_status);
                issuesDao.setProjectId(projectId);
                issuesDao.setCreator(SessionUtils.getUserId());
                jiraList.add(issuesDao);
            }
            startAt += maxResults;
        } while (issues.size() >= maxResults);
        return jiraList;
    }

    private void validateConfig(JiraConfig config) {
        jiraClientV2.setConfig(config);
        if (config == null) {
            MSException.throwException("jira config is null");
        }
    }

    public List<JiraIssueType> getIssueTypes(String jiraKey) {
        return jiraClientV2.getIssueType(jiraKey);
    }

    @Override
    public IssuesWithBLOBs addIssue(IssuesUpdateRequest issuesRequest) {

        setUserConfig();
        Project project = getProject();
        List<File> imageFiles = getImageFiles(issuesRequest);
        JSONObject addJiraIssueParam = buildUpdateParam(issuesRequest, getIssueType(project.getIssueConfig()), project.getJiraKey());

        JiraAddIssueResponse result = jiraClientV2.addIssue(JSONObject.toJSONString(addJiraIssueParam));
        JiraIssue issues = jiraClientV2.getIssues(result.getId());

        // 上传图片附件
        imageFiles.forEach(img -> jiraClientV2.uploadAttachment(result.getKey(), img));

        // 更新图片附件
        issuesRequest.setPlatformId(result.getKey());
        JSONObject param = buildUpdateParam(issuesRequest, getIssueType(project.getIssueConfig()), project.getJiraKey());
        jiraClientV2.updateIssue(result.getKey(), JSONObject.toJSONString(param));

        String status = getStatus(issues.getFields());
        issuesRequest.setPlatformStatus(status);

        issuesRequest.setPlatformId(result.getKey());
        issuesRequest.setId(UUID.randomUUID().toString());

        // 插入缺陷表
        IssuesWithBLOBs res = insertIssues(issuesRequest);

        // 用例与第三方缺陷平台中的缺陷关联
        handleTestCaseIssues(issuesRequest);

        return res;
    }

    public Project getProject() {
      return super.getProject(this.projectId, Project::getJiraKey);
    }

    private List<File> getImageFiles(IssuesUpdateRequest issuesRequest) {
        List<File> files = getImageFiles(issuesRequest.getDescription());
        List<CustomFieldItemDTO> customFields = CustomFieldService.getCustomFields(issuesRequest.getCustomFields());
        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            if (StringUtils.isNotBlank(fieldName)) {
                if (item.getValue() != null) {
                    if (StringUtils.isNotBlank(item.getType())) {
                        if (StringUtils.equalsAny(item.getType(),  "richText")) {
                            files.addAll(getImageFiles(item.getValue().toString()));
                        }
                    }
                }
            }
        });
        return files;
    }

    /**
     * 参数比较特殊，需要特别处理
     * @param fields
     */
    private void setSpecialParam(JSONObject fields) {
        Project project = getProject();
        int maxResults = 50, startAt = 0;
        try {
            Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClientV2.getCreateMetadata(project.getJiraKey(), getIssueType(project.getIssueConfig()));
            List<JiraUser> userOptions = jiraClientV2.getAssignableUser(project.getJiraKey(), startAt, maxResults);

            Boolean isUserKey = false;
            if (CollectionUtils.isNotEmpty(userOptions) && StringUtils.isBlank(userOptions.get(0).getAccountId())) {
                isUserKey = true;
            }

            for (String key : createMetadata.keySet()) {
                JiraCreateMetadataResponse.Field item = createMetadata.get(key);
                JiraCreateMetadataResponse.Schema schema = item.getSchema();
                if (schema == null) {
                    continue;
                }
                if (schema.getCustom() != null && schema.getCustom().endsWith("sprint")) {
                    try {
                        JSONObject field = fields.getJSONObject(key);
                        // sprint 传参数比较特殊，需要要传数值
                        fields.put(key, field.getInteger("id"));
                    } catch (Exception e) {}
                }
                if (isUserKey) {
                    if (schema.getType() != null && schema.getType().endsWith("user")) {
                    JSONObject field = fields.getJSONObject(key);
                        // 如果不是用户ID，则是用户的key，参数调整为key
                        JSONObject newField = new JSONObject();
                        newField.put("name", field.getString("id"));
                        fields.put(key, newField);
                    }
                    if (schema.getCustom() != null && schema.getCustom().endsWith("multiuserpicker")) { // 多选用户列表
                        try {
                            JSONArray userItems = fields.getJSONArray(key);
                            userItems.forEach(i ->
                                    ((JSONObject) i).put("name", ((JSONObject) i).getString("id")));
                        } catch (Exception e) {LogUtil.error(e);}
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private JSONObject buildUpdateParam(IssuesUpdateRequest issuesRequest, String issuetypeStr, String jiraKey) {

        issuesRequest.setPlatform(key);
        JSONObject fields = new JSONObject();
        JSONObject project = new JSONObject();

        String desc = issuesRequest.getDescription();

        fields.put("project", project);
        project.put("key", jiraKey);

        JSONObject issuetype = new JSONObject();
        issuetype.put("id", issuetypeStr);
        fields.put("issuetype", issuetype);

        JSONObject addJiraIssueParam = new JSONObject();
        addJiraIssueParam.put("fields", fields);

        if (issuesRequest.isThirdPartPlatform()) {
            parseCustomFiled(issuesRequest, fields);
            issuesRequest.setTitle(fields.getString("summary"));
        } else {
            fields.put("summary", issuesRequest.getTitle());
            fields.put("description", desc);
            parseCustomFiled(issuesRequest, fields);
        }
        setSpecialParam(fields);

        return addJiraIssueParam;
    }

    private String msDescription2Jira(IssuesUpdateRequest issuesRequest, String msDescription) {

        msDescription = msImg2JiraSoftPath(issuesRequest, msDescription);
        return msDescription;
//        return msDescription.replaceAll("\\n", "<br/>");
    }

    public String msImg2JiraSoftPath(IssuesUpdateRequest issuesRequest, String input) {
        // ![中心主题.png](/resource/md/get/a0b19136_中心主题.png) -> !{jira ENDPOINT}/secure/attachment/xxx.png！
        // !content!
        JiraIssue latestIssue = jiraClientV2.getIssues(issuesRequest.getPlatformId());
        List<IssueAttachment> attachmentList = getAttachments(latestIssue.getFields());
        String regex = "(\\!\\[.*?\\]\\((.*?)\\))";
        Pattern pattern = Pattern.compile(regex);
        if (StringUtils.isBlank(input)) {
            return "";
        }
        Matcher matcher = pattern.matcher(input);
        String result = input;
        while (matcher.find()) {
            String path = matcher.group(2);
            if (path.startsWith("http")) {
                String mdLink = "!" + path + "!";
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            } else if (path.startsWith("/resource")) {
                String name = "";
                String mdLink = "";
                if (path.contains("/resource/md/get/")) { // 兼容旧数据
                    name = path.substring(path.indexOf("/resource/md/get/") + 26);
                }
                else if (path.contains("/resource/md/get")) { //新数据走这里
                    name = path.substring(path.indexOf("/resource/md/get") + 26);
                }
                for (int i = 0; i < attachmentList.size(); i++) {
                    IssueAttachment issueAttachment = attachmentList.get(i);
                    try {
                        String fileName = URLEncoder.encode(issueAttachment.getFilename(), "UTF-8");
                        String content = issueAttachment.getContent();
                        if (fileName.equals(name)) {
                            mdLink = "!" + content + "!";
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            }
        }
        return result;
    }

    private void parseCustomFiled(IssuesUpdateRequest issuesRequest, JSONObject fields) {
        List<CustomFieldItemDTO> customFields = CustomFieldService.getCustomFields(issuesRequest.getCustomFields());

        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            if (StringUtils.isNotBlank(fieldName)) {
                if (item.getValue() != null) {
                    if (StringUtils.isNotBlank(item.getType())) {
                        if (StringUtils.equalsAny(item.getType(), "select", "radio", "member")) {
                            JSONObject param = new JSONObject();
                            if (fieldName.equals("assignee") || fieldName.equals("reporter")) {
                                if (issuesRequest.isThirdPartPlatform()) {
                                    param.put("id", item.getValue());
                                } else {
                                    param.put("name", item.getValue());
                                }
                            } else {
                                param.put("id", item.getValue());
                            }
                            fields.put(fieldName, param);
                        } else if (StringUtils.equalsAny(item.getType(),  "multipleSelect", "checkbox", "multipleMember")) {
                            JSONArray attrs = new JSONArray();
                            if (item.getValue() != null) {
                                JSONArray values = (JSONArray)item.getValue();
                                values.forEach(v -> {
                                    JSONObject param = new JSONObject();
                                    param.put("id", v);
                                    attrs.add(param);
                                });
                            }
                            fields.put(fieldName, attrs);
                        } else if (StringUtils.equalsAny(item.getType(),  "cascadingSelect")) {
                            if (item.getValue() != null) {
                                JSONObject attr = new JSONObject();
                                if (item.getValue() instanceof JSONArray) {
                                    JSONArray values = (JSONArray) item.getValue();
                                    if (CollectionUtils.isNotEmpty(values)) {
                                        if (values.size() > 0) {
                                            attr.put("id", values.get(0));
                                        }
                                        if (values.size() > 1) {
                                            JSONObject param = new JSONObject();
                                            param.put("id", values.get(1));
                                            attr.put("child", param);
                                        }
                                    }
                                } else {
                                    attr.put("id", item.getValue());
                                }
                                fields.put(fieldName, attr);
                            }
                        } else if (StringUtils.equalsAny(item.getType(),  "richText")) {
                            if (issuesRequest.getPlatformId() !=null) {
                                fields.put(fieldName, msDescription2Jira(issuesRequest, item.getValue().toString()));
                            } else {
                                fields.put(fieldName,item.getValue().toString());
                            }
                            if (fieldName.equals("description")) {
                                issuesRequest.setDescription(item.getValue().toString());
                            }
                        } else {
                            fields.put(fieldName, item.getValue());
                        }
                    }

                }
            }
        });
    }

    @Override
    public void updateIssue(IssuesUpdateRequest request) {
        setUserConfig();
        Project project = getProject();
        List<File> imageFiles = getImageFiles(request);
        imageFiles.forEach(img -> jiraClientV2.uploadAttachment(request.getPlatformId(), img));
        JSONObject param = buildUpdateParam(request, getIssueType(project.getIssueConfig()), project.getJiraKey());
        handleIssueUpdate(request);
        jiraClientV2.updateIssue(request.getPlatformId(), JSONObject.toJSONString(param));
    }

    @Override
    public void deleteIssue(String id) {
        IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(id);
        super.deleteIssue(id);
        jiraClientV2.deleteIssue(issuesWithBLOBs.getPlatformId());
    }

    @Override
    public void testAuth() {
        jiraClientV2.auth();
    }

    @Override
    public void userAuth(UserDTO.PlatformInfo userInfo) {
        setUserConfig(userInfo);
        jiraClientV2.auth();
    }

    @Override
    public List<PlatformUser> getPlatformUser() {
        return null;
    }


    @Override
    public void syncIssues(Project project, List<IssuesDao> issues) {
        super.isThirdPartTemplate = isThirdPartTemplate();
        IssuesService issuesService = CommonBeanFactory.getBean(IssuesService.class);
        if (project.getThirdPartTemplate()) {
            super.defaultCustomFields =  issuesService.getCustomFieldsValuesString(getThirdPartTemplate().getCustomFields());
        }
        issues.forEach(item -> {
            try {
                IssuesWithBLOBs issuesWithBLOBs = issuesMapper.selectByPrimaryKey(item.getId());
                // 更新缺陷表
                JiraIssue latestIssue = jiraClientV2.getIssues(item.getPlatformId());
                getUpdateIssue(item, latestIssue);

                if(issuesWithBLOBs instanceof IssuesWithBLOBs) {
//                    System.out.println("issuesWithBLOBs 存在，issue表有对应的jira平台缺陷");
                    issuesMapper.updateByPrimaryKeySelective(item);
                }
                else {
//                    System.out.println("issuesWithBLOBs 是一个空值，issue表无对应的jira平台缺陷");
                    issuesMapper.insert(item);
                }
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    // 标记成删除
                    item.setPlatformStatus(IssuesStatus.DELETE.toString());
                    issuesMapper.deleteByPrimaryKey(item.getId());
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        });
    }

    @Override
    public String getProjectId(String projectId) {
        return getProjectId(projectId, Project::getJiraKey);
    }

    public JiraConfig getConfig() {
        return getConfig(key, JiraConfig.class);
    }

    public JiraConfig setConfig() {
        JiraConfig config = getConfig();
        validateConfig(config);
        jiraClientV2.setConfig(config);
        return config;
    }

    public JiraConfig setUserConfig(UserDTO.PlatformInfo userInfo) {
        JiraConfig config = getConfig();
        if (userInfo != null && StringUtils.isNotBlank(userInfo.getJiraAccount())
                && StringUtils.isNotBlank(userInfo.getJiraPassword())) {
            config.setAccount(userInfo.getJiraAccount());
            config.setPassword(userInfo.getJiraPassword());
        }
        validateConfig(config);
        jiraClientV2.setConfig(config);
        return config;
    }

    public JiraConfig setUserConfig() {
        return setUserConfig(getUserPlatInfo(this.workspaceId));
    }

    public IssueTemplateDao getThirdPartTemplate() {
        Set<String> ignoreSet = new HashSet() {{
            add("timetracking");
            add("attachment");
        }};
        String projectKey = getProjectId(this.projectId);
        Project project = getProject();

        Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClientV2.getCreateMetadata(projectKey, getIssueType(project.getIssueConfig()));

        String userOptions = getUserOptions(projectKey);
        List<CustomFieldDao> fields = new ArrayList<>();
        char filedKey = 'A';
        for (String name : createMetadata.keySet()) {
            JiraCreateMetadataResponse.Field item = createMetadata.get(name);
            if (ignoreSet.contains(name)) {
                continue;  // timetracking, attachment todo
            }
            JiraCreateMetadataResponse.Schema schema = item.getSchema();
            CustomFieldDao customFieldDao = new CustomFieldDao();
            customFieldDao.setKey(String.valueOf(filedKey++));
            customFieldDao.setId(name);
            customFieldDao.setCustomData(name);
            customFieldDao.setName(item.getName());
            customFieldDao.setRequired(item.isRequired());
            setCustomFiledType(schema, customFieldDao, userOptions);
            setCustomFiledDefaultValue(customFieldDao, item);
            JSONArray options = getAllowedValuesOptions(item.getAllowedValues());
            if (options != null)
                customFieldDao.setOptions(options.toJSONString());
            fields.add(customFieldDao);
        }

        fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType()))
                .collect(Collectors.toList());

        // 按类型排序，富文本排最后，input 排最前面，summary 排第一个
        fields.sort((a, b) -> {
            if (a.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return 1;
            if (b.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return -1;
            if (a.getId().equals("summary")) return -1;
            if (b.getId().equals("summary")) return 1;
            if (a.getType().equals(CustomFieldType.INPUT.getValue())) return -1;
            if (b.getType().equals(CustomFieldType.INPUT.getValue())) return 1;
            return a.getType().compareTo(b.getType());
        });
        IssueTemplateDao issueTemplateDao = new IssueTemplateDao();
        issueTemplateDao.setCustomFields(fields);
        issueTemplateDao.setPlatform(this.key);
        return issueTemplateDao;
    }

    public String getIssueType(String configStr) {
        ProjectIssueConfig projectConfig = super.getProjectConfig(configStr);
        String jiraIssueType = projectConfig.getJiraIssueType();
        if (StringUtils.isBlank(jiraIssueType)) {
            MSException.throwException("请在项目中配置 Jira 问题类型！");
        }
        return jiraIssueType;
    }

    public String getStoryType(String configStr) {
        ProjectIssueConfig projectConfig = super.getProjectConfig(configStr);
        String jiraStoryType = projectConfig.getJiraStoryType();
       if (StringUtils.isBlank(jiraStoryType)) {
            MSException.throwException("请在项目中配置 Jira 需求类型！");
        }
        return jiraStoryType;
    }

    private void setCustomFiledType(JiraCreateMetadataResponse.Schema schema, CustomFieldDao customFieldDao, String userOptions) {
        Map<String, String> fieldTypeMap = new HashMap() {{
            put("summary", CustomFieldType.INPUT.getValue());
            put("description", CustomFieldType.RICH_TEXT.getValue());
            put("components", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("fixVersions", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("versions", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("priority", CustomFieldType.SELECT.getValue());
            put("environment", CustomFieldType.RICH_TEXT.getValue());
            put("labels", CustomFieldType.MULTIPLE_INPUT.getValue());
        }};
        String customType = schema.getCustom();
        String value = null;
        if (StringUtils.isNotBlank(customType)) {
            // 自定义字段
            if (customType.contains("multiselect")) {
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
            } else if (customType.contains("cascadingselect")) {
                value = "cascadingSelect";
            } else if (customType.contains("multiuserpicker")) {
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
                customFieldDao.setOptions(userOptions);
            } else if (customType.contains("userpicker")) {
                value = CustomFieldType.SELECT.getValue();
                customFieldDao.setOptions(userOptions);
            } else if (customType.contains("multicheckboxes")) {
                value = CustomFieldType.CHECKBOX.getValue();
                customFieldDao.setDefaultValue(JSONObject.toJSONString(new ArrayList<>()));
            } else if (customType.contains("radiobuttons")) {
                value = CustomFieldType.RADIO.getValue();
            } else if (customType.contains("textfield")) {
                value = CustomFieldType.INPUT.getValue();
            } else if (customType.contains("datetime")) {
                value = CustomFieldType.DATETIME.getValue();
            } else if (customType.contains("datepicker")) {
                value = CustomFieldType.DATE.getValue();
            } else if (customType.contains("float")) {
                value = CustomFieldType.FLOAT.getValue();
            } else if (customType.contains("select")) {
                value = CustomFieldType.SELECT.getValue();
            } else if (customType.contains("url")) {
                value = CustomFieldType.INPUT.getValue();
            } else if (customType.contains("textarea")) {
                value = CustomFieldType.TEXTAREA.getValue();
            } else if (customType.contains("labels")) {
                value = CustomFieldType.MULTIPLE_INPUT.getValue();
            }
        } else {
            // 系统字段
            value = fieldTypeMap.get(customFieldDao.getId());
            String type = schema.getType();
            if ("user".equals(type)) {
                value = CustomFieldType.SELECT.getValue();
                customFieldDao.setOptions(userOptions);
            } else if ("date".equals(type)) {
                value = CustomFieldType.DATE.getValue();
            } else if ("datetime".equals(type)) {
                value = CustomFieldType.DATETIME.getValue();
            }
        }
        customFieldDao.setType(value);
    }

    private void setCustomFiledDefaultValue(CustomFieldDao customFieldDao, JiraCreateMetadataResponse.Field item) {
        if (item.isHasDefaultValue()) {
            Object defaultValue = item.getDefaultValue();
            if (defaultValue != null) {
                Object msDefaultValue;
                if (defaultValue instanceof JSONObject) {
                    msDefaultValue = ((JSONObject) defaultValue).get("id");
                } else if (defaultValue instanceof JSONArray) {
                    List defaultList = new ArrayList();
                    ((JSONArray) defaultValue).forEach(i -> {
                        if (i instanceof JSONObject) {
                            JSONObject obj = (JSONObject) i;
                            defaultList.add(obj.get("id"));
                        } else {
                            defaultList.add(i);
                        }
                    });

                    msDefaultValue = defaultList;
                } else {
                    if (customFieldDao.getType().equals(CustomFieldType.DATE.getValue())) {
                        msDefaultValue = Instant.ofEpochMilli((Long) defaultValue).atZone(ZoneId.systemDefault()).toLocalDate();
                    } else if (customFieldDao.getType().equals(CustomFieldType.DATETIME.getValue())) {
                        msDefaultValue = LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) defaultValue), ZoneId.systemDefault()).toString();
                    } else {
                        msDefaultValue = defaultValue;
                    }
                }
                customFieldDao.setDefaultValue(JSONObject.toJSONString(msDefaultValue));
            }
        }
    }

    private JSONArray getAllowedValuesOptions(List<JiraCreateMetadataResponse.AllowedValues> allowedValues) {
        if (allowedValues != null) {
            JSONArray options = new JSONArray();
            allowedValues.forEach(val -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("value", val.getId());
                if (StringUtils.isNotBlank(val.getName())) {
                    jsonObject.put("text", val.getName());
                } else {
                    jsonObject.put("text", val.getValue());
                }
                JSONArray children = getAllowedValuesOptions(val.getChildren());
                if (children != null) {
                    jsonObject.put("children", children);
                }
                options.add(jsonObject);
            });
            return options;
        }
        return null;
    }

    private String getUserOptions(String projectKey) {
        int maxResults = 50, startAt = 0;
        List<JiraUser> jiraUserList;
        List<JiraUser> userOptions = new ArrayList<>();
        do {
            jiraUserList = jiraClientV2.getAssignableUser(projectKey, startAt, maxResults);
            if (jiraUserList.size() !=0 ) {
                startAt += maxResults;
                userOptions.addAll(jiraUserList);
            }
        }
        while (jiraUserList.size() != 0);
//        List<JiraUser> userOptions = jiraClientV2.getAssignableUser(projectKey, startAt, maxResults);
        JSONArray options = new JSONArray();
        userOptions.forEach(val -> {
            JSONObject jsonObject = new JSONObject();
            if (StringUtils.isNotBlank(val.getAccountId())) {
                jsonObject.put("value", val.getAccountId());
            } else {
                jsonObject.put("value", val.getName());
            }
            jsonObject.put("text", val.getDisplayName());
            options.add(jsonObject);
        });
        return options.toJSONString();
    }
}
