import { get } from '@/utils/request'

export const getHealth = () => get<string>('/v1/health')
