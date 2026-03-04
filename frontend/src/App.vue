<template>
  <div id="app-root">
    <header class="topbar">
      <h1>寰宇智导 前端演示</h1>
      <nav>
        <router-link to="/">主页</router-link>
        <router-link to="/travel">寰宇智导应用</router-link>
        <router-link to="/manus">AI 超级智能体</router-link>
      </nav>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
#app-root { max-width: 900px; margin: 20px auto; }
.topbar { display:flex; align-items:center; justify-content:space-between; }
.topbar nav a { margin-left: 12px; }
</style>
