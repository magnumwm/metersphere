<template>
  <div v-loading="result.loading">
    <el-dialog :close-on-click-modal="false" :title="title" :visible.sync="createVisible" destroy-on-close
               @close="handleClose">
      <el-form :model="form" :rules="rules" ref="form" label-position="right" label-width="60px" size="small">
        <el-form-item :label="$t('commons.name')" prop="name">
          <el-input v-model="form.name" autocomplete="off" show-word-limit maxlength="50"></el-input>
        </el-form-item>
        <el-form-item :label="$t('commons.description')" prop="description">
          <el-input v-model="form.description" autocomplete="off" type="textarea" show-word-limit maxlength="200"></el-input>
        </el-form-item>
        <el-form-item>
          <environment-group-row ref="environmentGroupRow" :env-group-id="environmentId" :read-only="false"/>
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div class="dialog-footer">
          <ms-dialog-footer
            btn-size="medium"
            @cancel="createVisible = false"
            @confirm="submit('form')"/>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import MsDialogFooter from "@/business/components/common/components/MsDialogFooter";
import EnvironmentGroupRow from "@/business/components/settings/workspace/environment/EnvironmentGroupRow";

export default {
  name: "EditEnvironmentGroup",
  components: {
    EnvironmentGroupRow,
    MsDialogFooter
  },
  data() {
    return {
      title: '创建用户组',
      form: {},
      createVisible: false,
      rules: {},
      environmentGroup: {},
      environmentId: "",
      result: {}
    }
  },
  methods: {
    handleClose() {},
    submit() {
      let envGroupProject = this.$refs.environmentGroupRow.envGroupProject;
      let param = {
        envGroupProject,
        name: this.form.name,
        description: this.form.description
      }
      this.result = this.$post("/environment/group/add", param, () => {
        this.$success(this.$t('commons.save_success'));
        this.createVisible = false;
        this.$emit("refresh");
      });
    },
    open() {
      this.createVisible = true;
    }
  }
}
</script>

<style scoped>

</style>
