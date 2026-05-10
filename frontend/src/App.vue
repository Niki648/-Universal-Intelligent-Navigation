<template>
  <div id="app-root">
    <header class="topbar">
      <router-link class="brand" to="/">
        <span class="brand-mark">*</span>
        <span>
          <strong>Wayfinder Guild</strong>
          <small>&#35753; AI &#25214;&#21040;&#27491;&#30830;&#30340;&#36335;</small>
        </span>
      </router-link>
      <nav aria-label="Main navigation">
        <router-link to="/">Map</router-link>
        <router-link to="/profile">Profile</router-link>
        <router-link to="/projects">Projects</router-link>
        <router-link to="/travel-agent">Travel Agent</router-link>
        <router-link to="/manus-agent">Tool Agent</router-link>
      </nav>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>
