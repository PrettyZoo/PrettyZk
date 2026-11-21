import { reactive } from 'vue'

// Shared backend lifecycle state. The shell renders immediately; API calls
// wait until the Rust shell injects the backend port.
export const backendState = reactive({
  ready: false,
  error: '',
})
