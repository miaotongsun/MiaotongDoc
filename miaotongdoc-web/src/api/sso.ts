import api from './index'

export const ssoApi = {
  getProviders() {
    return api.get<any, any>('/sso/providers')
  },

  logout() {
    return api.post<any, any>('/sso/logout')
  }
}
