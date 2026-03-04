import Home from '../views/Home.vue'
import TravelChat from '../views/TravelChat.vue'
import ManusChat from '../views/ManusChat.vue'

export default [
  { path: '/', name: 'Home', component: Home },
  { path: '/travel', name: 'TravelChat', component: TravelChat },
  { path: '/manus', name: 'ManusChat', component: ManusChat }
]
