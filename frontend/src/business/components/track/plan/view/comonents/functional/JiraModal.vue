<template>
  <div>
    <el-button type="primary" size="small" @click="checkJiraAuth">提交到Jira</el-button>
    <el-dialog
      title="提交bug到jira"
      :visible.sync="issuesDialog"
      width="45%"
      :modal-append-to-body='false'
      append-to-body>
      <el-form :model="issuesForm" label-position="right" label-width="100px" size="medium" ref="form" :rules="issuesRules">
        <el-form-item label="所属项目" prop="project">
          <el-select v-model="issuesForm.project" placeholder="请选择所属项目" @change="handleProjectChange" style="width: 300px" size="small">
            <el-option :title="item.name" v-for="item in projectList" :key="item.id" :label="item.name" :value="item.key"/>
          </el-select>
        </el-form-item>
        <el-form-item label="问题类型" prop="issuetype">
          <el-select v-model="issuesForm.issuetype" placeholder="请选择" @change="handleIssuetypeChange" style="width: 300px" size="small">
            <el-option :title="item.name" v-for="item in issuetypes" :key="item.id" :label="item.name" :value="item.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="严重等级" prop="priority">
          <el-select v-model="issuesForm.priority" placeholder="请选择" style="width: 300px" size="small">
            <el-option :title="item.name" v-for="item in prioritys" :key="item.id" :label="item.name" :value="item.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷标题" prop="summary">
          <el-input size="small" placeholder="请输入标题" style="width: 80%" v-model="issuesForm.summary"/>
        </el-form-item>
        <el-form-item v-if="customItems && customItems.length > 0" label="自定义项" prop="customItems">
          <el-select v-for="customItem in customItems" v-model="subCustomItems[customItem.schema.customId].id" :key="customItem.schema.customId" :placeholder="customItem.name" @change="changeCustom($event,customItem.schema.customId)" style="width: 150px" size="small">
            <el-option :title="item.value" v-for="item in customItem.allowedValues" :key="item.id" :label="item.value" :value="item.id"/>
          </el-select>
        </el-form-item>
        <el-form-item label="解决版本" prop="version">
          <el-select v-model="issuesForm.version" placeholder="请选择解决版本" style="width: 300px" size="small">
            <el-option :title="item.name" v-for="item in fixVersions" :key="item.name" :label="item.name" :value="item.name"/>
          </el-select>
        </el-form-item>
        <el-form-item label="指给人员" prop="assignUser">
          <el-select v-model="issuesForm.assignUser" :loading="searchLoading" placeholder="搜索assignee" style="width: 300px" size="small" filterable remote reserve-keyword :remote-method="searchAssignUser">
            <el-option :title="item.displayName" v-for="item in jiraAssignList" :key="item.key" :label="item.displayName" :value="item.key"/>
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务" prop="linkTask">
          <el-select v-model="issuesForm.linkType" placeholder="关联类型" style="width: 100px" size="small">
            <el-option :title="item.outward" v-for="item in issueLinkTypes" :key="item.id" :label="item.outward" :value="item.id"/>
          </el-select>
          <el-select v-model="issuesForm.issuesPicker" :loading="searchTaskLoading" placeholder="关联任务选择" style="width: 300px" size="small" filterable remote reserve-keyword :remote-method="searchTask">
            <el-option :title="item.summaryText" v-for="item in issuesPickers" :key="item.key" :label="item.summaryText" :value="item.key"/>
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷描述" prop="description">
          <el-input class="ms-http-textarea" v-model="issuesForm.description"
                    type="textarea"
                    :autosize="{ minRows: 3, maxRows: 10}"
                    :rows="3" size="small"/>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <ms-dialog-footer
          @cancel="issuesDialog = false"
          @confirm="submitJiraIssues('form')"/>
      </template>
    </el-dialog>
    <el-dialog
      title="登录Jira"
      :visible.sync="loginDialog"
      width="45%"
      :modal-append-to-body='false'
      append-to-body>
      <el-form :model="loginForm" label-position="right" label-width="100px" size="medium" ref="form" :rules="rules">
        <el-form-item label="用户名" prop="username">
          <el-input size="small" v-model="loginForm.username"/>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input size="small" v-model="loginForm.password"/>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <ms-dialog-footer
          @cancel="loginDialog = false"
          @confirm="submit('form')"/>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import {getCurrentUser} from "@/common/js/utils";
import MsDialogFooter from "../../../../../common/components/MsDialogFooter";
export default {
  name: "JiraModal",
  components: {
    MsDialogFooter
  },
  data() {
    return {
      project: {},
      searchTaskLoading: false,
      searchLoading: false,
      issuesDialog: false,
      loginDialog: false,
      loginForm: {},
      issuesForm: {},
      subCustomItems: {},
      subCustomArrItems: [],
      projectList: [],
      prioritys: [],
      issuetypes: [],
      fixVersions: [],
      customItems: [],
      issueLinkTypes: [],
      jiraAssignList: [],
      issuesPickers: [],
      reporter: '',
      issuesRules: {
        project: {required: true, message: "项目不可为空", trigger: ['blur', 'change']},
        issuetype: {required: true, message: "问题类型不可为空", trigger: ['blur', 'change']},
      },
      rules: {
        username: {required: true, message: "用户名不可为空", trigger: ['blur', 'change']},
        password: {required: true, message: "密码不可为空", trigger: ['blur', 'change']}
      },
    }
  },
  methods: {
    init(user){
      this.reporter = user.id;
    },
    submit(form) {
      this.$refs[form].validate((valid) => {
        if (valid) {
          const loginData = {
            username: this.loginForm.username,
            password: this.loginForm.password,
          }
          this.queryJiraAuth(loginData)
        } else {
          return false;
        }
      });
    },
    submitJiraIssues(form) {
      this.$refs[form].validate((valid) => {
        if (valid) {
          console.log('issuesForm', this.issuesForm)
          let BaseParams = {};
          BaseParams = {
            fields: {
              issuetype: {
                id: this.issuesForm.issuetype,
              },
              project: {
                key: this.issuesForm.project,
              },
              summary: this.issuesForm.summary,
              assignee: {
                name: this.issuesForm.assignUser,
              },
              reporter: {
                name: this.reporter,
              },
              priority: {
                id: this.issuesForm.priority,
              },
              description: this.issuesForm.description,
            },
          };
          if (this.issuesForm.version) {
            BaseParams.fields.fixVersions = [{ name: this.issuesForm.version }];
          }
          if (this.issuesForm.linkType && this.issuesForm.issuesPicker) {
            const issueslinks = {
              issuelinks: [
                {
                  add: {
                    type: {
                      id: this.issuesForm.linkType,
                    },
                    outwardIssue: {
                      key: this.issuesForm.issuesPicker,
                    },
                  },
                },
              ],
            };
            BaseParams.update = issueslinks;
          }
          if (this.customItems && this.customItems.length > 0) {
            if (this.customItems.length !== this.subCustomArrItems.length) {
              this.$info('请先选择完整自定义必填项！！');
              return false;
            } else {
              this.subCustomArrItems.forEach(item => {
                BaseParams.fields[`customfield_${item.toString()}`] = this.subCustomItems[item];
              });
            }
          }
          this.queryJiraIssueSubmit(BaseParams)
        } else {
          return false;
        }
      });
    },
    queryJiraAuth(loginData){
      this.$jiraLogin(loginData).then(
        res => {
          if(res && !res.needVerify){
            this.loginDialog = false;
          }
        }
      )
    },
    queryJiraSession(){
      this.$get(`/jira/rest/auth/latest/session?_t=${new Date().getTime().toString()}`,res => {
        if (!res && res.name){
          this.loginDialog = true
        }else {
          this.loginDialog = false
          this.issuesDialog = true
          this.queryJiraProjectList()
          this.queryJiraIssuetypes()
          this.queryIssueLinkTypes()
        }
      }, true)
    },
    queryJiraProjectList(){
      this.$get(`/jira/rest/api/latest/project`,res => {
        if (res){
          this.projectList = res
        }
      }, true)
    },
    queryIssueLinkTypes(){
      this.$get(`/jira/rest/api/latest/issueLinkType`,res => {
        if (res){
          this.issueLinkTypes = res.issueLinkTypes
        }
      }, true)
    },
    queryJiraIssuetypes(){
      this.$get(`/jira/rest/api/latest/issuetype`,res => {
        if (res){
          this.issuetypes = res
        }
      }, true)
    },
    queryJiraIssueSubmit(BaseParams){
      this.$post(`/jira/rest/api/latest/issue`,BaseParams,res => {
        if (res){
          this.$emit("saveIssues",this.issuesForm.summary,this.issuesForm.description, res.key);
          this.issuesDialog = false;
        }
      }, function () {}, true)
    },
    queryCreatemeta(params){
      this.$get(`/jira/rest/api/latest/issue/createmeta?issuetypeIds=${params.issuetype.toString()}&expand=projects.issuetypes.fields&projectKeys=${params.key.toString()}`,res => {
        if (res && res.projects.length > 0){
          const fixVersions = [];
          try {
            res.projects[0].issuetypes[0].fields.fixVersions.allowedValues.forEach(
              item => {
                if (!item.released) {
                  fixVersions.push(item);
                }
              }
            );
          } catch (err) {
            this.$warning('该项目没有可指定的解决版本');
            return;
          }
          const customItems = [];
          const customCreatemeta = res.projects[0].issuetypes[0].fields;
          for (const item in customCreatemeta) {
            if (
              customCreatemeta[item].required &&
              customCreatemeta[item].allowedValues &&
              item.indexOf('customfield') > -1
            ) {
              customItems.push(customCreatemeta[item]);
            }
          }
          this.prioritys= res.projects[0].issuetypes[0].fields.priority.allowedValues,
            this.fixVersions = fixVersions
          if (customItems && customItems.length > 0){
            customItems.forEach(item => {
              this.$set(this.subCustomItems,item.schema.customId,{ id: item.allowedValues[0].id })
              this.subCustomArrItems.push(item.schema.customId);
            })
            this.customItems = customItems
          }
        } else {
          this.$warning('该项目没有可指定的解决版本');
        }
      }, true)
    },
    queryJiraAssignList(params){
      this.$get(`/jira/rest/api/latest/user/assignable/search?project=${params.key.toString()}&username=${params.searchWord.toString()}`,res => {
        if (res && res.length > 0){
          this.jiraAssignList = res
        }
      }, true)
    },
    queryJiraPickers(params){
      const headers = {
        Accept: 'application/json',
      }
      this.$get(`/jira/rest/api/1.0/issues/picker?currentProjectId=&showSubTaskParent=true&showSubTasks=true&currentIssueKey=null&query=${params.searchWord.toString()}`,res => {
        if (res &&  res.sections.length > 0){
          this.issuesPickers = res.sections[0].issues
        }
      }, true, headers)
    },
    checkJiraAuth(){
      this.queryJiraSession()
    },
    handleProjectChange(){
      const {project, issuetype} = this.issuesForm;
      if (project && issuetype){
        this.queryCreatemeta({key: project, issuetype})
      }
    },
    handleIssuetypeChange(){
      const {project, issuetype} = this.issuesForm;
      console.log(project, issuetype)
      if (project && issuetype){
        this.queryCreatemeta({key: project, issuetype})
      }
    },
    searchAssignUser(value){
      const {project} = this.issuesForm;
      if (value && project){
        this.queryJiraAssignList({key:project, searchWord: value})
      }
    },
    changeCustom(value, key ){
      const { subCustomItems, subCustomArrItems } = this;
      this.$set(subCustomItems,key,{ id: value })
      if (subCustomArrItems.indexOf(key) === -1) {
        subCustomArrItems.push(key);
      }
      this.subCustomArrItems = subCustomArrItems
    },
    searchTask(value){
      const {project} = this.issuesForm;
      if (value && project){
        this.queryJiraPickers({key:project, searchWord: value})
      }
    },
  },
  created() {
    let user = getCurrentUser();
    this.init(user);
  },
}
</script>

<style scoped>
.jira-content {
  z-index: 2002!important;
}
</style>
