import {coverFor} from '../../shared/visual';
import {useGet} from '../../shared/api';
export function MiniCover({id}:{id:number}){const c=useGet(`/api/courses/${id}`,{},!!id),category=useGet(`/api/categories/${c.data?.categoryId}`,{},!!c.data?.categoryId);return <img className="dashboard-course-cover" src={coverFor(category.data?.name)} alt="" loading="lazy"/>}
