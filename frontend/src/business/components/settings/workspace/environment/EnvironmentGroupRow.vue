<template>
  <div>
    <el-form v-loading="result.loading" class="row-form">
      <el-form-item v-for="(item,index) in envGroupProject" :key="index">
        <el-row type="flex" justify="space-between" :gutter="10">
          <el-col :span="6">
            <el-select v-model="item.projectId" filterable clearable style="width: 100%" @change="projectChange(item)" size="mini"
                       placeholder="请选择项目" :disabled="readOnly">
              <el-option v-for="(project, projectIndex) in projectList" :key="projectIndex" :label="project.name" :value="project.id"></el-option>
            </el-select>
          </el-col>

          <el-col :span="6">
            <el-select v-model="item.environmentId" filterable clearable style="width: 100%" @change="environmentChange(item)" size="mini"
                       placeholder="请选择环境" :disabled="readOnly">
              <el-option v-for="(environment, envIndex) in item.environments" :key="envIndex" :label="environment.name" :value="environment.id"></el-option>
            </el-select>
          </el-col>

          <el-col :span="4">
            <el-button size="mini" icon="el-icon-s-data" style="width: 100%;"
                       @click="showDomainInfo(item)" v-if="item.moreDomain">查看域名详情</el-button>
            <el-input v-else v-model="item.domainName" :disabled="true" size="mini"/>
          </el-col>

          <el-col :span="6">
            <el-input prop="description" show-overflow-tooltip placeholder="备注" size="mini" :disabled="readOnly"/>
          </el-col>

          <el-col :span="2">
            <el-button type="danger" icon="el-icon-delete" circle size="mini" :disabled="readOnly"></el-button>
          </el-col>
        </el-row>
      </el-form-item>
    </el-form>

    <el-dialog title="域名列表" :visible.sync="domainVisible">
      <el-table :data="conditions">
        <el-table-column prop="socket" :label="$t('load_test.domain')" show-overflow-tooltip width="180">
          <template v-slot:default="{row}">
            {{getUrl(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('api_test.environment.condition_enable')" show-overflow-tooltip min-width="100px">
          <template v-slot:default="{row}">
            {{getName(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="details" show-overflow-tooltip min-width="120px" :label="$t('api_test.value')">
          <template v-slot:default="{row}">
            {{getDetails(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" show-overflow-tooltip min-width="120px" :label="$t('commons.create_time')">
          <template v-slot:default="{row}">
            <span>{{ row.time | timestampFormatDate }}</span>
          </template>
        </el-table-column>
      </el-table>
      <span slot="footer" class="dialog-footer">
    <el-button @click="domainVisible = false" size="mini">{{$t('commons.cancel')}}</el-button>
    <el-button type="primary" @click="domainVisible = false" size="mini">{{$t('commons.confirm')}}</el-button>
  </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "EnvironmentGroupRow",
  data() {
    return {
      result: {},
      envGroupProject: [],
      projectList: [],
      environmentList: [],
      environments: [],
      domainVisible: false,
      conditions: []
    }
  },
  props: {
    envGroupId: {
      type: String,
      default() {
        return '';
      }
    },
    readOnly: {
      type: Boolean,
      default() {
        return true;
      }
    }
  },
  created() {
    this.getEnvironmentGroupProject();
    this.getProjects();
  },
  methods: {
    projectChange(item) {
      if (item && item.projectId) {
        this.$get('/api/environment/list/' + item.projectId, res => {
          this.$set(item, 'environments', res.data);
        });
      } else {
        this.$set(item, 'environments', []);
      }
      this.$set(item, 'environmentId', "");
      this.$set(item, 'domainName', '');
    },
    environmentChange(item) {
      // todo 优化
      // 环境改变时初始化判断状态
      this.$set(item, "moreDomain", false);
      this.$set(item, "domainName", '');
      let environments = item.environments;
      let index = environments.findIndex(e => e.id === item.environmentId);
      if (index === -1) {
        this.$set(item, "domainName", '');
        return ;
      }
      let environment = environments[index].config;
      if (environment) {
        const config = JSON.parse(environment);
        if (config.httpConfig && !config.httpConfig.conditions) {
          if (config.httpConfig.protocol && config.httpConfig.domain) {
            let domain = config.httpConfig.protocol + "://" + config.httpConfig.domain;
            this.$set(item, "domainName", domain);
            return ;
          }
        } else {
          if (config.httpConfig.conditions.length === 1) {
            let obj = config.httpConfig.conditions[0];
            if (obj.protocol && obj.domain) {
              this.$set(item, "domainName", obj.protocol + "://" + obj.domain);
              return;
            }
          } else if (config.httpConfig.conditions.length > 1){
            this.$set(item, "moreDomain", true);
            return;
          }
        }
      } else {
        this.$set(item, "domainName", environment.protocol + '://' + environment.domain);
        return ;
      }
      this.$set(item, "domainName", "");
    },
    showDomainInfo(item) {
      const index = item.environments.findIndex(e => e.id === item.environmentId);
      if (index === -1) {
        return "";
      }
      let environment = item.environments[index]
      const config = JSON.parse(environment.config);
      this.conditions = config.httpConfig.conditions;
      this.domainVisible = true;
    },
    getProjects() {
      // 工作空间下项目
      this.result = this.$get("/project/listAll", response => {
        this.projectList = response.data;
      })
    },
    getEnvironmentGroupProject() {
      let url = '/environment/group/project/list/' + this.envGroupId;
      this.result = this.$get(url, response => {
        this.envGroupProject = response.data;
        // 初始化环境数据
        this.envGroupProject.forEach(env => {
          let {projectId} = env;
          this.$get('/api/environment/list/' + projectId, res => {
            this.$set(env, 'environments', res.data);
            this._parseDomainName(env);
          });
        })
      })
    },
    _parseDomainName(item) {
      let {environmentId} = item;
      const index = item.environments.findIndex(e => e.id === environmentId);
      if (index === -1) {
        this.$set(item, "domainName", "");
        return;
      }
      let environment = item.environments[index].config;
      if (environment) {
        const config = JSON.parse(environment);
        if (config.httpConfig && !config.httpConfig.conditions) {
          if (config.httpConfig.protocol && config.httpConfig.domain) {
            let domain = config.httpConfig.protocol + "://" + config.httpConfig.domain;
            this.$set(item, "domainName", domain);
          }
        } else {
          if (config.httpConfig.conditions.length === 1) {
            let obj = config.httpConfig.conditions[0];
            if (obj.protocol && obj.domain) {
              this.$set(item, "domainName", obj.protocol + "://" + obj.domain);
            }
          }
        }
      } else {
        this.$set(item, "domainName", environment.protocol + '://' + environment.domain);
      }
    },
    getName(row) {
      switch (row.type) {
        case "NONE":
          return this.$t("api_test.definition.document.data_set.none");
        case "MODULE":
          return this.$t("test_track.module.module");
        case "PATH":
          return this.$t("api_test.definition.api_path");
      }
    },
    getUrl(row) {
      return row.protocol + "://" + row.socket;
    },
    getDetails(row) {
      if (row && row.type === "MODULE") {
        if (row.details && row.details instanceof Array) {
          let value = "";
          row.details.forEach((item) => {
            value += item.name + ",";
          });
          if (value.endsWith(",")) {
            value = value.substr(0, value.length - 1);
          }
          return value;
        }
      } else if (row && row.type === "PATH" && row.details.length > 0 && row.details[0].name) {
        return row.details[0].value === "equals" ? this.$t("commons.adv_search.operators.equals") + row.details[0].name : this.$t("api_test.request.assertions.contains") + row.details[0].name;
      } else {
        return "";
      }
    },
  }
}
</script>

<style scoped>
.row-form >>> .el-form-item {
  margin-bottom: 0;
}
</style>
