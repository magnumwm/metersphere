<template>
  <div>
    <el-form-item :label-width="labelWidth" :label="$t('project.jira_key')">
      <el-input v-model="form.jiraKey" autocomplete="off" @blur="getIssueTypeOption"/>
      <ms-instructions-icon effect="light">
        <template>
          <img class="jira-image" src="@/assets/jira-key.png"/>
        </template>
      </ms-instructions-icon>
    </el-form-item>
    <el-form-item :label-width="labelWidth" :label="$t('organization.integration.jira_issuetype')" prop="issuetype">
      <el-select filterable v-model="form.issueConfigObj.jiraIssueType">
        <el-option v-for="item in issueTypes" :key="item.id" :label="item.name" :value="item.id">
        </el-option>
      </el-select>
    </el-form-item>
    <el-form-item :label-width="labelWidth" :label="$t('organization.integration.jira_storytype')" prop="storytype">
      <el-select filterable v-model="form.issueConfigObj.jiraStoryType">
        <el-option v-for="item in issueTypes" :key="item.id" :label="item.name" :value="item.id">
        </el-option>
      </el-select>
    </el-form-item>
    <el-form-item :label-width="labelWidth" :label="$t('organization.integration.jira_version')" prop="version">
      <el-select filterable v-model="form.issueConfigObj.jiraVersion">
        <el-option v-for="item in versions" :key="item.id" :label="item.name" :value="item.id">
        </el-option>
      </el-select>
    </el-form-item>
  </div>
</template>

<script>
import MsInstructionsIcon from "@/business/components/common/components/MsInstructionsIcon";
import {getJiraIssueType, getJiraVersion} from "@/network/Issue";
import {getCurrentWorkspaceId} from "@/common/js/utils";
export default {
  name: "ProjectJiraConfig",
  components: {MsInstructionsIcon},
  props: ['labelWidth', 'form'],
  data() {
    return {
      issueTypes: [],
      versions: []
    }
  },
  mounted() {
   this.getIssueTypeOption();
  },
  methods: {
    getIssueTypeOption() {
      this.issueTypes = [];
      getJiraIssueType({
        projectId: this.form.id,
        workspaceId: getCurrentWorkspaceId(),
        jiraKey: this.form.jiraKey
      }, (data) => {
        this.issueTypes = data;
      });

      this.versions = [];
      getJiraVersion({
        projectId: this.form.id,
        workspaceId: getCurrentWorkspaceId(),
        jiraKey: this.form.jiraKey
      }, (data) => {
        this.versions = data;
      });
    }
  }
}
</script>

<style scoped>
.el-input, .el-textarea {
  width: 80%;
}
</style>
