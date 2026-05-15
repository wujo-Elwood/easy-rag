import request from '../utils/request'

export function uploadFile(kbId, file) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', kbId)
  return request.post('/api/file/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export function getFileList(kbId) {
  return request.get(`/api/file/list/${kbId}`)
}

/** 重新处理文件：清除旧切片和向量，重新解析 */
export function reprocessFile(id) {
  return request.post(`/api/file/${id}/reprocess`)
}

export function deleteFile(id) {
  return request.delete(`/api/file/${id}`)
}
