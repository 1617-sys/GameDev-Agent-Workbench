<template>
  <section class="state-page" role="alert" aria-labelledby="forbidden-title">
    <span class="state-code">403</span>
    <h1 id="forbidden-title">没有访问权限</h1>
    <p>当前账号不具备此功能的后端能力授权。请联系项目管理员调整角色，而不是重试或修改页面地址。</p>
    <RouterLink class="button primary" to="/projects">返回项目中心</RouterLink>
  </section>
</template>
