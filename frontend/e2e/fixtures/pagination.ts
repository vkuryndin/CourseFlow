const pageOf=(items:unknown[],page=0,size=20,total=items.length)=>({content:items,page,size,totalElements:total,totalPages:Math.ceil(total/size),first:page===0,last:(page+1)*size>=total});
export {pageOf};
