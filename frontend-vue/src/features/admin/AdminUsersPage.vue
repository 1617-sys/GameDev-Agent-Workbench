<script setup>
import { onMounted, reactive, ref } from "vue";
import { usersApi } from "../../shared/api/users.js";
import AsyncState from "../../shared/ui/AsyncState.vue";
const filters = reactive({ pageNum: 1, pageSize: 20, username: "", role: "", status: "" });
const users = ref([]), total = ref(0), loading = ref(false), error = ref(""), audits = ref([]), busy = ref("");
async function load(){ loading.value=true; error.value=""; try { const page=await usersApi.list(filters); users.value=page.records||[]; total.value=Number(page.total||0); } catch(e){ error.value=e.message; } finally { loading.value=false; } }
async function change(user, field, value){ if (value===user[field]) return; if (!confirm(`确认将 ${user.username} 的${field === 'role' ? '角色' : '状态'}改为 ${value}？`)) return; busy.value=`${user.id}:${field}`; try { await usersApi.update(user.id, { [field]: value }); await load(); } catch(e){ error.value=e.message; } finally { busy.value=""; } }
async function showAudits(user){ try { audits.value=await usersApi.audits(user.id); } catch(e){ error.value=e.message; } }
onMounted(load);
</script>
<template><main class="page-shell"><header><p class="eyebrow">管理员 / 用户与角色</p><h1>用户权限管理</h1><p>角色和账号状态由服务端强制校验并记录审计。</p></header>
<form class="toolbar" @submit.prevent="load"><input v-model.trim="filters.username" placeholder="用户名"/><select v-model="filters.role"><option value="">全部角色</option><option>USER</option><option>PROJECT_ADVANCED</option><option>ADMIN</option></select><select v-model="filters.status"><option value="">全部状态</option><option>NORMAL</option><option>DISABLED</option></select><button>筛选</button></form>
<AsyncState :loading="loading" :error="error" :empty="!users.length" empty-text="未找到用户"><div class="table-scroll"><table><thead><tr><th>用户名</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td><select :value="user.role" :disabled="busy || user.self" @change="change(user,'role',$event.target.value)"><option>USER</option><option>PROJECT_ADVANCED</option><option>ADMIN</option></select></td><td><select :value="user.status" :disabled="busy || user.self" @change="change(user,'status',$event.target.value)"><option>NORMAL</option><option>DISABLED</option></select></td><td>{{ user.createdAt || '—' }}</td><td><button type="button" @click="showAudits(user)">审计</button><span v-if="user.self">当前账号不可修改</span></td></tr></tbody></table></div></AsyncState><p>共 {{ total }} 位用户</p><section v-if="audits.length" class="card"><h2>变更审计</h2><pre>{{ audits }}</pre></section></main></template>
