package io.metersphere.track.issue.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.dto.IssueAttachment;
import io.metersphere.i18n.Translator;
import io.metersphere.service.SystemParameterService;
import io.metersphere.track.issue.domain.jira.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.jsoup.safety.Whitelist;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class JiraAbstractClient extends BaseClient {

    protected  String ENDPOINT;

    protected  String PREFIX;

    protected  String USER_NAME;

    protected  String PASSWD;

    public JiraIssue getIssues(String issuesId) {
        LogUtil.info("getIssues: " + issuesId);
        ResponseEntity<String> responseEntity;
        responseEntity = restTemplate.exchange(getBaseUrl() + "/issue/" + issuesId + "?expand=renderedFields", HttpMethod.GET, getAuthHttpEntity(), String.class);
        return  (JiraIssue) getResultForObject(JiraIssue.class, responseEntity);
    }

    public Map<String, JiraCreateMetadataResponse.Field> getCreateMetadata(String projectKey, String issueType) {
//        String url = getBaseUrl() + "/issue/createmeta?projectKeys={1}&issuetypeNames={2}&expand=projects.issuetypes.fields";
        // 获取创建issue模板
        String url = getBaseUrl() + "/issue/createmeta?projectKeys={1}&issuetypeIds={2}&expand=projects.issuetypes.fields";
        ResponseEntity<String> response = null;
        Map<String, JiraCreateMetadataResponse.Field> fields = null;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, projectKey, issueType);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        try {
             fields = ((JiraCreateMetadataResponse) getResultForObject(JiraCreateMetadataResponse.class, response))
                    .getProjects().get(0).getIssuetypes().get(0).getFields();
        } catch (Exception e) {
            MSException.throwException(Translator.get("issue_jira_info_error"));
        }
        fields.remove("project");
        fields.remove("issuetype");
        return fields;
    }

    public List<JiraIssueType> getIssueType(String projectKey) {
        JiraIssueProject project = getProject(projectKey);
//        String url = getUrl("/issuetype/project?projectId={0}");
        String url = getUrl("/issuetype");
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, project.getId());
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return (List<JiraIssueType>) getResultForList(JiraIssueType.class, response);
    }

    public JiraIssueProject getProject(String projectKey) {
        String url = getUrl("/project/" + projectKey);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return (JiraIssueProject) getResultForObject(JiraIssueProject.class, response);
    }

    public List<JiraUser> getAssignableUser(String projectKey, int startAt, int maxResults) {
        String url = getBaseUrl() + "/user/assignable/search?project={1}" + "&maxResults=" + maxResults + "&startAt=" + startAt;
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, getAuthHttpEntity(), String.class, projectKey);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return (List<JiraUser>) getResultForList(JiraUser.class, response);
    }

    // 按jira 修复版本获取story需求
    public JSONArray getDemands(String projectKey, String issueType, String fixVersion, int startAt, int maxResults) {
        String jql = getBaseUrl() + "/search?jql=project=" + projectKey + "+AND+issuetype=" + issueType + "+ AND+fixVersion="
                + fixVersion + "&maxResults=" + maxResults + "&startAt=" + startAt + "&fields=summary,issuetype";
        ResponseEntity<String> responseEntity = restTemplate.exchange(jql,
                HttpMethod.GET, getAuthHttpEntity(), String.class);
        JSONObject jsonObject = JSONObject.parseObject(responseEntity.getBody());
        return jsonObject.getJSONArray("issues");
    }

    public List<JiraField> getFields() {
        ResponseEntity<String> response = restTemplate.exchange(getBaseUrl() + "/field", HttpMethod.GET, getAuthHttpEntity(), String.class);
        return (List<JiraField>) getResultForList(JiraField.class, response);
    }

    public JiraAddIssueResponse addIssue(String body) {
        LogUtil.info("addIssue: " + body);
        HttpHeaders headers = getAuthHeader();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(getBaseUrl() + "/issue", HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return (JiraAddIssueResponse) getResultForObject(JiraAddIssueResponse.class, response);
    }

    public void updateIssue(String id, String body) {
        LogUtil.info("editIssue: " + body);
        HttpHeaders headers = getAuthHeader();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        try {
           restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.PUT, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    public void deleteIssue(String id) {
        LogUtil.info("deleteIssue: " + id);
        try {
            restTemplate.exchange(getBaseUrl() + "/issue/" + id, HttpMethod.DELETE, getAuthHttpEntity(), String.class);
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() != 404) {// 404说明jira没有，可以直接删
                MSException.throwException(e.getMessage());
            }
        }
    }

    public void uploadAttachment(String issueKey, File file) {
        HttpHeaders authHeader = getAuthHeader();
        authHeader.add("X-Atlassian-Token", "no-check");
        authHeader.setContentType(MediaType.parseMediaType("multipart/form-data; charset=UTF-8"));

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        FileSystemResource fileResource = new FileSystemResource(file);
        paramMap.add("file", fileResource);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(paramMap, authHeader);
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(getBaseUrl() + "/issue/" + issueKey + "/attachments", HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        System.out.println(response.getBody());
    }

    public void auth() {
        try {
            restTemplate.exchange(getBaseUrl() + "/myself", HttpMethod.GET, getAuthHttpEntity(), String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
    }

    protected HttpEntity<MultiValueMap> getAuthHttpEntity() {
        return new HttpEntity<>(getAuthHeader());
    }

    protected HttpHeaders getAuthHeader() {
        return getBasicHttpHeaders(USER_NAME, PASSWD);
    }

    protected String getBaseUrl() {
        return ENDPOINT + PREFIX;
    }

    protected String getUrl(String path) {
        return getBaseUrl() + path;
    }

    public String jiraHtmlDesc2MsDesc(String htmlDesc) {
        String desc = jiraHtmlImg2MsImg(htmlDesc);
        Document document = Jsoup.parse(desc);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        document.select("br").append("\\n");
        document.select("p").prepend("\\n\\n");
        String desc_tmp = document.html().replaceAll("\\\\n", "\n");

        String desc_clean = Jsoup.clean(desc_tmp, "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));
        return  desc_clean.replace("&nbsp;", "");
    }

    protected String jiraHtmlImg2MsImg(String input) {
        // <img src=\"/secure/attachment/10018/10018_file.jpg\"/> ->  ![10018_file.jpg](${jira_baseUrl}/secure/attachment/10018/10018_file.jpg)
        // <img src=\"https://mystephen.atlassian.net/rest/api/2/attachment/content/10050\" /> ![10050](${jira_baseUrl}/rest/api/2/attachment/content/10050)
        String regex = "(<img\\s*src=\\\"(.*?)\\\".*?>)";
        SystemParameterService parameterService = CommonBeanFactory.getBean(SystemParameterService.class);
        Pattern pattern = Pattern.compile(regex);
        if (StringUtils.isBlank(input)) {
            return "";
        }
        Matcher matcher = pattern.matcher(input);
        String result = input;
        while (matcher.find()) {
            String url = matcher.group(2);
            String name ="";
            if (url.startsWith(ENDPOINT)) {
                String[] buff = url.split("/");
                name = buff[buff.length-1];
                String mdLink = "![" + name + "](" + url + ")";
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            }
            else if (url.startsWith("/secure/attachment/") || url.startsWith("/rest/api/2/attachment/")) { // jira地址链接不全
                String path = url.substring(url.indexOf("/secure/attachment/"));
                String[] buff = path.split("/");
                name = buff[buff.length-1];
                String mdLink = "![" + name + "](" + ENDPOINT + path + ")";
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            }
            else {
                assert parameterService != null;
                if (url.startsWith(parameterService.getValue("base.url"))) { // 如果是ms本地上传地址，则去掉base.url
                    String path = url.replaceFirst(parameterService.getValue("base.url"), "");
                    String[] buff = url.split("/");
                    name = buff[buff.length-1];
                    String mdLink = "![" + name + "](" + path + ")";
                    result = matcher.replaceFirst(mdLink);
                    matcher = pattern.matcher(result);
                }
            }
        }
        return result;
    }

    public void setConfig(JiraConfig config) {
        if (config == null) {
            MSException.throwException("config is null");
        }
        String url = config.getUrl();

        if (StringUtils.isNotBlank(url) && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        ENDPOINT = url;
        USER_NAME = config.getAccount();
        PASSWD = config.getPassword();
    }

    public JSONArray getProjectIssues(int startAt, int maxResults, String projectKey, String issueType, String affectedVersion) {
        ResponseEntity<String> responseEntity;
        responseEntity = restTemplate.exchange(getBaseUrl() + "/search?startAt={1}&maxResults={2}&jql=project={3}+AND+issuetype={4}+AND+affectedVersion={4}&expand=renderedFields",
                HttpMethod.GET, getAuthHttpEntity(), String.class, startAt, maxResults, projectKey, issueType, affectedVersion);
        JSONObject jsonObject = JSONObject.parseObject(responseEntity.getBody());
        return jsonObject.getJSONArray("issues");
//        return  (JiraIssueListResponse)getResultForObject(JiraIssueListResponse.class, responseEntity);
    }

    public List<JiraVersion> getProjectVersions(String projectKey) {
        ResponseEntity<String> responseEntity;
        responseEntity = restTemplate.exchange(getBaseUrl() + "/project/" + projectKey + "/versions", HttpMethod.GET, getAuthHttpEntity(), String.class);
        return (List<JiraVersion>) getResultForList(JiraVersion.class, responseEntity);
    }
}
