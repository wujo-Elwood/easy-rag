import request from '../utils/request'

export function getKbList() {
  return request.get('/api/kb')
}

export function createKb(data) {
  return request.post('/api/kb', data)
}

export function deleteKb(id) {
  return request.delete(`/api/kb/${id}`)
}
