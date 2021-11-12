<template>
  <div>
    <el-card class="table-card" v-loading="result.loading">
      <template v-slot:header>
        <ms-table-header :create-tip="btnTips" :condition.sync="condition"
                         @search="search" @create="createEnvironment">
        </ms-table-header>
      </template>
      <el-table :data="environmentGroupList"
                style="width: 100%" ref="table"
                row-key="id"
                @expand-change="expandChange"
                :height="screenHeight">
        <el-table-column type="expand" prop="id">
          <template v-slot:default="scope">
            <environment-group-row :env-group-id="scope.row.id" ref="environmentGroupRow"
                                   :read-only="!scope.row.readOnly"
                                   style="overflow-x:hidden; overflow-y: auto; height: 180px;"/>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.name')" width="300" prop="name" show-overflow-tooltip/>
        <el-table-column prop="createTime" :label="$t('commons.create_time')" width="300">
          <template v-slot:default="scope">
            <span>{{ scope.row.createTime | timestampFormatDate }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('commons.operating')">
          <template v-slot:default="scope">
            <div>
              <ms-table-operator @editClick="editEnvironment(scope.row)" @deleteClick="deleteEnvironment(scope.row)">
                <template v-slot:middle>
                  <ms-table-operator-button :tip="$t('commons.copy')" @exec="copyEnvironment(scope.row)"
                                            icon="el-icon-document-copy" type="info"/>
                </template>
              </ms-table-operator>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <ms-table-pagination :change="init" :current-page.sync="currentPage" :page-size.sync="pageSize"
                           :total="total"/>
    </el-card>

    <edit-environment-group ref="editEnvironmentGroup" @refresh="init"/>
    <ms-delete-confirm :title="'删除环境组'" @delete="_handleDelete" ref="deleteConfirm"/>

  </div>
</template>

<script>
import MsTableHeader from "@/business/components/common/components/MsTableHeader";
import MsTableButton from "@/business/components/common/components/MsTableButton";
import MsTableOperator from "@/business/components/common/components/MsTableOperator";
import MsTableOperatorButton from "@/business/components/common/components/MsTableOperatorButton";
import MsTablePagination from "@/business/components/common/pagination/TablePagination";
import ApiEnvironmentConfig from "@/business/components/api/test/components/ApiEnvironmentConfig";
import EnvironmentEdit from "@/business/components/api/test/components/environment/EnvironmentEdit";
import MsAsideItem from "@/business/components/common/components/MsAsideItem";
import MsAsideContainer from "@/business/components/common/components/MsAsideContainer";
import ProjectSwitch from "@/business/components/common/head/ProjectSwitch";
import SearchList from "@/business/components/common/head/SearchList";
import EnvironmentImport from "@/business/components/project/menu/EnvironmentImport";
import EnvironmentGroupRow from "@/business/components/settings/workspace/environment/EnvironmentGroupRow";
import EditEnvironmentGroup from "@/business/components/settings/workspace/environment/EditEnvironmentGroup";
import MsDeleteConfirm from "@/business/components/common/components/MsDeleteConfirm";

export default {
  name: "EnvironmentGroup",
  components: {
    EditEnvironmentGroup,
    EnvironmentImport,
    SearchList,
    ProjectSwitch,
    MsAsideContainer,
    MsAsideItem,
    EnvironmentEdit,
    ApiEnvironmentConfig,
    MsTablePagination,
    MsTableOperatorButton,
    MsTableOperator,
    MsTableButton,
    MsTableHeader,
    EnvironmentGroupRow,
    MsDeleteConfirm
  },
  data() {
    return {
      btnTips: '创建环境组',
      envGroupId: '',
      condition: {},
      environmentGroupList: [],
      result: {},
      currentPage: 1,
      pageSize: 10,
      total: 0,
      screenHeight: 'calc(100vh - 210px)',
    }
  },
  created() {

  },
  activated() {
    this.init();
  },
  methods: {
    init() {
      this.result = this.$post("/environment/group/list/" + this.currentPage + '/' + this.pageSize, this.condition, res => {
        let data = res.data;
        let {listObject, itemCount} = data;
        this.environmentGroupList = listObject;
        this.total = itemCount;
      })
    },
    createEnvironment() {
      this.$refs.editEnvironmentGroup.open();
    },
    editEnvironment(row) {
      this.$refs.table.toggleRowExpansion(row, true);
      this.$set(row, "readOnly", true);
    },
    deleteEnvironment(row) {
      if (row.system) {
        this.$warning(this.$t('group.admin_not_allow_delete'));
        return;
      }
      this.$refs.deleteConfirm.open(row);
    },
    copyEnvironment() {

    },
    search() {

    },
    _handleDelete(row) {
      this.$get('/environment/group/delete/' + row.id, () => {
        this.$success(this.$t('commons.delete_success'));
        this.init();
      })
    },
    expandChange(row, expanded) {
      if (expanded) {
        this.$set(row, "readOnly", false);
      }
    },
  },
}
</script>

<style scoped>

</style>
