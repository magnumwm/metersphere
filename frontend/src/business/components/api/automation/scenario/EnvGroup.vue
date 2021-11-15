<template>
  <div>
    <div style="margin-left: 20px;">
      <el-select v-model="envGroupId" placeholder="请选择用户组" style="margin-top: 8px;width: 200px;" size="small">
        <!--  todo  如果缺少对应的项目进行提示 -->
        <el-option v-for="(group, index) in groups" :key="index"
                   :label="group.name"
                   :value="group.id"/>
      </el-select>
      <span style="margin-left: 8px;">环境组</span>
    </div>
    <el-button type="primary" @click="handleConfirm" size="small" class="env-confirm">确 定</el-button>
  </div>
</template>

<script>
import {getCurrentProjectID} from "@/common/js/utils";

export default {
  name: "EnvGroup",
  components: {},
  data() {
    return {
      groups: [],
      envGroupId: this.groupId
    }
  },
  props: {
    groupId: {
      type: String,
      default() {
        return "";
      }
    }
  },
  watch: {
    groupId(val) {
      this.envGroupId = val;
    }
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      // todo  params 查询包含当前项目ID的工作空间下用户组。
      // 如果项目组中缺少项目项 在选项上进行警告提示，并且禁用选项。
      this.$get('/environment/group/get/' + getCurrentProjectID(), res => {
        let data = res.data;
        this.groups = data ? data : [];
      })
    },
    handleConfirm() {
      this.$emit("setEnvGroup", this.envGroupId);
      this.$emit('close');
    },

  }
}
</script>

<style scoped>
.env-confirm {
  margin-left: 20px;
  width: 360px;
  margin-top: 10px;
}
</style>
